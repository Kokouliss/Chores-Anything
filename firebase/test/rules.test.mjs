// Runs firestore.rules on the local emulator. Every write below has the same shape as the one
// the Android app sends (see core/data/.../firebase), so a rule that would break the app fails here.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  Timestamp,
  arrayUnion,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

const CODE = 'ABCD2345';
const HOUSEHOLD = 'households/h1';
const CHORE = `${HOUSEHOLD}/chores/c1`;
const ASKED = `${HOUSEHOLD}/chores/c2`;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-chores-anything',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

// A household owned by "owner" with an adult "member" and a "kid", an open chore for anyone, and a
// chore the kid asked for.
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, HOUSEHOLD), {
      name: 'Family',
      ownerId: 'owner',
      memberIds: ['owner', 'member', 'kid'],
      members: { owner: { name: 'Owner', role: 'adult' }, member: { name: 'Member', role: 'adult' }, kid: { name: 'Kid', role: 'kid' } },
      inviteCode: CODE,
      invitedEmails: ['guest@example.com'],
      createdAt: new Date(),
    });
    await setDoc(doc(db, `invites/${CODE}`), { householdId: 'h1' });
    await setDoc(doc(db, CHORE), chore({ createdById: 'owner', createdByName: 'Owner' }));
    await setDoc(doc(db, ASKED), chore({ title: 'Drive me to the cinema', status: 'pending', createdById: 'kid', createdByName: 'Kid' }));
    await setDoc(doc(db, `${HOUSEHOLD}/chores/c3`), chore({ title: 'Pocket money', status: 'pending', createdById: 'kid', createdByName: 'Kid' }));
  });
});

function chore(fields) {
  return { title: 'Dishes', info: '', needs: [], rotation: [], status: 'open', createdAt: new Date(), ...fields };
}

function as(uid, email, emailVerified = true) {
  const token = email ? { email, email_verified: emailVerified } : {};
  return env.authenticatedContext(uid, token).firestore();
}
const owner = () => as('owner', 'owner@example.com');
const member = () => as('member', 'member@example.com');
const kid = () => as('kid', 'kid@example.com');
const stranger = () => as('stranger', 'stranger@example.com');

// What the app writes for a new chore, in the writer's name.
function newChore(uid, name, fields) {
  return chore({ createdById: uid, createdByName: name, createdAt: serverTimestamp(), ...fields });
}

test('members read the household and its chores, strangers read nothing', async () => {
  await assertSucceeds(getDoc(doc(member(), HOUSEHOLD)));
  await assertSucceeds(getDocs(collection(kid(), `${HOUSEHOLD}/chores`)));
  await assertFails(getDoc(doc(stranger(), HOUSEHOLD)));
  await assertFails(getDoc(doc(stranger(), CHORE)));
});

test('a code lets a stranger join as an adult, a wrong code does not', async () => {
  const join = (code) =>
    updateDoc(doc(stranger(), HOUSEHOLD), { memberIds: arrayUnion('stranger'), 'members.stranger': { name: 'New', role: 'adult', inviteCode: code } });
  await assertFails(join('WRONG234'));
  await assertSucceeds(join(CODE));
});

test('only the owner changes roles, and never their own', async () => {
  await assertSucceeds(updateDoc(doc(owner(), HOUSEHOLD), { 'members.member.role': 'kid' }));
  await assertFails(updateDoc(doc(member(), HOUSEHOLD), { 'members.kid.role': 'adult' }));
  await assertFails(updateDoc(doc(kid(), HOUSEHOLD), { 'members.kid.role': 'adult' }));
  await assertFails(updateDoc(doc(owner(), HOUSEHOLD), { 'members.owner.role': 'kid' }));
});

test('an adult adds an open chore, a kid only a waiting one', async () => {
  await assertSucceeds(setDoc(doc(member(), `${HOUSEHOLD}/chores/new1`), newChore('member', 'Member', {})));
  await assertFails(setDoc(doc(kid(), `${HOUSEHOLD}/chores/new2`), newChore('kid', 'Kid', {})));
  await assertSucceeds(setDoc(doc(kid(), `${HOUSEHOLD}/chores/new3`), newChore('kid', 'Kid', { status: 'pending' })));
  // Never in someone else's name, and never already done.
  await assertFails(setDoc(doc(member(), `${HOUSEHOLD}/chores/new4`), newChore('owner', 'Owner', {})));
  await assertFails(setDoc(doc(member(), `${HOUSEHOLD}/chores/new5`), newChore('member', 'Member', { doneAt: new Date(), doneById: 'member' })));
});

test('a chore keeps its limits', async () => {
  await assertFails(setDoc(doc(member(), `${HOUSEHOLD}/chores/long`), newChore('member', 'Member', { title: 'x'.repeat(81) })));
  await assertFails(setDoc(doc(member(), `${HOUSEHOLD}/chores/mins`), newChore('member', 'Member', { minutes: 0 })));
  await assertFails(setDoc(doc(member(), `${HOUSEHOLD}/chores/rhythm`), newChore('member', 'Member', { repeat: 'year' })));
  await assertFails(setDoc(doc(member(), `${HOUSEHOLD}/chores/extra`), newChore('member', 'Member', { secret: true })));
});

test('only an adult confirms or declines what a kid asked for; the kid changes or withdraws it', async () => {
  await assertFails(updateDoc(doc(kid(), ASKED), { status: 'open' }));
  await assertSucceeds(updateDoc(doc(kid(), ASKED), { title: 'Drive me to the cinema at 8' }));
  await assertSucceeds(deleteDoc(doc(kid(), `${HOUSEHOLD}/chores/c3`)));
  await assertSucceeds(updateDoc(doc(member(), ASKED), { status: 'open' }));
  // Once it is through, the kid can no longer take it back.
  await assertFails(deleteDoc(doc(kid(), ASKED)));
});

test('a kid does a chore, but does not change what it is', async () => {
  await assertSucceeds(updateDoc(doc(kid(), CHORE), { doneAt: new Date(), doneById: 'kid', doneByName: 'Kid' }));
  await assertFails(updateDoc(doc(kid(), CHORE), { title: 'Nothing' }));
  await assertFails(updateDoc(doc(kid(), CHORE), { minutes: 1 }));
  await assertFails(deleteDoc(doc(kid(), CHORE)));
  // An adult changes and deletes anything.
  await assertSucceeds(updateDoc(doc(member(), CHORE), { title: 'Dishes and pots', minutes: 20 }));
  await assertSucceeds(deleteDoc(doc(member(), CHORE)));
});

test('a hand-over goes in one\'s own name and is answered by the person asked', async () => {
  const handoff = { toId: 'kid', toName: 'Kid', byId: 'member', byName: 'Member' };
  await assertFails(updateDoc(doc(owner(), CHORE), { handoff }));
  await assertSucceeds(updateDoc(doc(member(), CHORE), { handoff }));
  await assertSucceeds(updateDoc(doc(kid(), CHORE), { handoff: deleteField(), assigneeId: 'kid', assigneeName: 'Kid' }));
});

test('who made a chore never changes', async () => {
  await assertFails(updateDoc(doc(member(), CHORE), { createdById: 'member' }));
  await assertFails(updateDoc(doc(owner(), CHORE), { createdAt: new Date() }));
});

test('history lines are written once, in one\'s own name, now, and expire within a year', async () => {
  const line = (fields) => ({
    kind: 'done', byId: 'kid', byName: 'Kid', choreId: 'c1', choreTitle: 'Dishes', at: serverTimestamp(),
    expireAt: Timestamp.fromMillis(Date.now() + 365 * 86_400_000), ...fields,
  });
  await assertSucceeds(setDoc(doc(kid(), `${HOUSEHOLD}/activity/a1`), line({ minutes: 15 })));
  await assertFails(setDoc(doc(member(), `${HOUSEHOLD}/activity/a2`), line({})));
  await assertFails(setDoc(doc(kid(), `${HOUSEHOLD}/activity/a3`), line({ kind: 'shouted' })));
  await assertFails(setDoc(doc(kid(), `${HOUSEHOLD}/activity/a4`), line({ expireAt: Timestamp.fromMillis(Date.now() + 400 * 86_400_000) })));
  await assertFails(setDoc(doc(stranger(), `${HOUSEHOLD}/activity/a5`), line({ byId: 'stranger' })));
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), `${HOUSEHOLD}/activity/a6`), line({})));
  await assertFails(updateDoc(doc(kid(), `${HOUSEHOLD}/activity/a6`), { kind: 'undone' }));
  await assertFails(deleteDoc(doc(owner(), `${HOUSEHOLD}/activity/a6`)));
});

test('a household is created with its invite in one batch', async () => {
  const db = owner();
  const batch = writeBatch(db);
  batch.set(doc(db, 'households/h2'), {
    name: 'Flat', ownerId: 'owner', memberIds: ['owner'], members: { owner: { name: 'Owner', role: 'adult' } },
    inviteCode: 'WXYZ6789', invitedEmails: [], createdAt: serverTimestamp(),
  });
  batch.set(doc(db, 'invites/WXYZ6789'), { householdId: 'h2' });
  await assertSucceeds(batch.commit());
  await assertFails(setDoc(doc(db, 'invites/QQQQ2222'), { householdId: 'h2' }));
});

test('only a person writes their own device tokens', async () => {
  await assertSucceeds(setDoc(doc(kid(), 'users/kid/tokens/t1'), { updatedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(kid(), 'users/owner/tokens/t1'), { updatedAt: serverTimestamp() }));
});
