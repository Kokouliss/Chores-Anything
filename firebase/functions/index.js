const functions = require('firebase-functions/v1');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { describeChange } = require('./notify');

initializeApp();

// First generation functions on purpose: they may run in any region, wherever the database is.
const europe = functions.region('europe-west1');

// Every change to a chore that someone else should hear about goes out at once: a kid's request to
// the adults, a confirmation to the kid, a hand-over to the person asked, a chore done to the rest.
exports.notifyChoreChange = europe.firestore
  .document('households/{householdId}/chores/{choreId}')
  .onWrite(async (change, context) => {
    const { householdId, choreId } = context.params;
    const db = getFirestore();
    const household = await db.doc(`households/${householdId}`).get();
    // The household was deleted while its chores were still going. Nothing to tell anyone.
    if (!household.exists) return;
    const what = describeChange(change.before.data(), change.after.data(), household.data());
    if (!what || what.to.length === 0) return;
    await notify(db, what.to, householdId, choreId, {
      title: (change.after.data() || change.before.data()).title,
      bodyLocKey: what.key,
      bodyLocArgs: what.args,
      channelId: 'updates',
      // One notification per chore: a newer one about the same chore replaces the older one.
      tag: choreId,
    });
  });

// Deleting a household takes everything under it along: its chores and its history. The app deletes
// only the household itself, also when the last member deletes their account.
exports.cleanUpHousehold = europe
  .runWith({ timeoutSeconds: 540 })
  .firestore.document('households/{householdId}')
  .onDelete((snapshot) => getFirestore().recursiveDelete(snapshot.ref));

/** One notification to every device of [people]. A tap opens the chore. */
async function notify(db, people, householdId, choreId, notification) {
  const devices = (await Promise.all(people.map((id) => db.collection(`users/${id}/tokens`).get()))).flatMap(
    (snapshot) => snapshot.docs,
  );
  if (devices.length === 0) return;

  const result = await getMessaging().sendEachForMulticast({
    tokens: devices.map((device) => device.id),
    // The app opens this chore when the notification is tapped.
    data: { householdId, choreId },
    android: { notification },
  });

  // Firebase no longer knows the address of an app that was removed, so forget it too.
  await Promise.all(
    result.responses.map((response, i) =>
      response.error?.code === 'messaging/registration-token-not-registered' ? devices[i].ref.delete() : null,
    ),
  );
}
