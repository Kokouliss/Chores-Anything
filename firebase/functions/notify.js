// Decides what a change to a chore says in a notification, and who hears it.
// Kept apart from index.js so that it can be tested without Firebase.

const millis = (time) => (time && time.toMillis ? time.toMillis() : time);

/** The member ids of the adults of a household (`members` maps id to `{ name, role }`; no role is an adult). */
exports.adults = (household) =>
  (household.memberIds || []).filter((id) => ((household.members || {})[id] || {}).role !== 'kid');

/** Everyone in the household but the people in `except`. */
const others = (household, except) => (household.memberIds || []).filter((id) => !except.includes(id));

/** Only people who are still in the household. */
const members = (household, ids) => ids.filter((id) => (household.memberIds || []).includes(id));

/**
 * `before` and `after` are the chore's fields, undefined when the chore did not or does not exist.
 * `household` has `memberIds` and `members`. Returns null for changes nobody needs to hear about,
 * otherwise `{ key, args, to }`: `key` names a string in the Android app
 * (app/src/main/res/values/strings.xml), so each phone shows the text in its own language, and
 * `to` is who hears it.
 */
exports.describeChange = (before, after, household) => {
  if (!before && !after) return null;
  const title = (after || before).title || '';

  if (!before) {
    if (after.status === 'pending') {
      return { key: 'push_needs_ok', args: [after.createdByName || '', title], to: others({ memberIds: exports.adults(household) }, [after.createdById]) };
    }
    if (after.assigneeId && after.assigneeId !== after.createdById) {
      return { key: 'push_assigned_by', args: [after.createdByName || '', title], to: members(household, [after.assigneeId]) };
    }
    return { key: 'push_added', args: [after.createdByName || '', title], to: others(household, [after.createdById]) };
  }

  if (!after) {
    // Only a chore that still waited was declined. A deleted open chore says nothing: it is simply gone.
    if (before.status === 'pending') return { key: 'push_declined', args: [title], to: members(household, [before.createdById]) };
    return null;
  }

  if (before.status === 'pending' && after.status === 'open') {
    return { key: 'push_confirmed', args: [title], to: members(household, [after.createdById]) };
  }
  if (after.status === 'pending') return null;

  if (after.doneAt && millis(after.doneAt) !== millis(before.doneAt)) {
    return { key: 'push_done', args: [after.doneByName || '', title], to: others(household, [after.doneById]) };
  }

  if (after.handoff && !before.handoff) {
    return { key: 'push_passed', args: [after.handoff.byName || '', title], to: members(household, [after.handoff.toId]) };
  }
  if (before.handoff && !after.handoff) {
    const took = after.assigneeId === before.handoff.toId;
    return { key: took ? 'push_took' : 'push_refused', args: [before.handoff.toName || '', title], to: members(household, [before.handoff.byId]) };
  }

  if (after.assigneeId && after.assigneeId !== before.assigneeId) {
    return { key: 'push_assigned', args: [title], to: members(household, [after.assigneeId]) };
  }
  return null;
};
