const test = require('node:test');
const assert = require('node:assert/strict');
const { adults, describeChange } = require('./notify');

const household = {
  memberIds: ['mom', 'dad', 'kid'],
  members: { mom: { name: 'Mom', role: 'adult' }, dad: { name: 'Dad' }, kid: { name: 'Nikos', role: 'kid' } },
};
const chore = { title: 'Dishes', status: 'open', createdById: 'mom', createdByName: 'Mom' };

test('a member without a role is an adult', () => {
  assert.deepEqual(adults(household), ['mom', 'dad']);
});

test('a kid asking for a chore is heard by the adults', () => {
  const asked = { ...chore, status: 'pending', createdById: 'kid', createdByName: 'Nikos' };
  assert.deepEqual(describeChange(undefined, asked, household), { key: 'push_needs_ok', args: ['Nikos', 'Dishes'], to: ['mom', 'dad'] });
});

test('a new chore is heard by its assignee, or by everyone else', () => {
  assert.deepEqual(describeChange(undefined, { ...chore, assigneeId: 'kid' }, household), { key: 'push_assigned_by', args: ['Mom', 'Dishes'], to: ['kid'] });
  assert.deepEqual(describeChange(undefined, chore, household), { key: 'push_added', args: ['Mom', 'Dishes'], to: ['dad', 'kid'] });
  // Assigned to oneself: the others hear it was added.
  assert.deepEqual(describeChange(undefined, { ...chore, assigneeId: 'mom' }, household).key, 'push_added');
});

test('confirming or declining is heard by the kid who asked', () => {
  const asked = { ...chore, status: 'pending', createdById: 'kid' };
  assert.deepEqual(describeChange(asked, { ...asked, status: 'open' }, household), { key: 'push_confirmed', args: ['Dishes'], to: ['kid'] });
  assert.deepEqual(describeChange(asked, undefined, household), { key: 'push_declined', args: ['Dishes'], to: ['kid'] });
  assert.equal(describeChange(chore, undefined, household), null);
  // Changes to a chore that still waits say nothing.
  assert.equal(describeChange(asked, { ...asked, title: 'Dishes!' }, household), null);
});

test('a chore done is heard by everyone but the one who did it', () => {
  const done = { ...chore, doneAt: 1000, doneById: 'kid', doneByName: 'Nikos' };
  assert.deepEqual(describeChange(chore, done, household), { key: 'push_done', args: ['Nikos', 'Dishes'], to: ['mom', 'dad'] });
  // Done again later, as a repeating chore is.
  assert.equal(describeChange(done, { ...done, doneAt: 2000 }, household).key, 'push_done');
  assert.equal(describeChange(done, { ...done, info: 'rinse first' }, household), null);
});

test('a hand-over is heard by the person asked, and the answer by the one who asked', () => {
  const handoff = { toId: 'dad', toName: 'Dad', byId: 'mom', byName: 'Mom' };
  const passed = { ...chore, handoff };
  assert.deepEqual(describeChange(chore, passed, household), { key: 'push_passed', args: ['Mom', 'Dishes'], to: ['dad'] });
  assert.deepEqual(describeChange(passed, { ...chore, assigneeId: 'dad' }, household), { key: 'push_took', args: ['Dad', 'Dishes'], to: ['mom'] });
  assert.deepEqual(describeChange(passed, chore, household), { key: 'push_refused', args: ['Dad', 'Dishes'], to: ['mom'] });
});

test('a new assignee hears it, nobody who left does', () => {
  assert.deepEqual(describeChange(chore, { ...chore, assigneeId: 'kid' }, household), { key: 'push_assigned', args: ['Dishes'], to: ['kid'] });
  assert.deepEqual(describeChange(chore, { ...chore, assigneeId: 'gone' }, household).to, []);
  assert.equal(describeChange(undefined, undefined, household), null);
});
