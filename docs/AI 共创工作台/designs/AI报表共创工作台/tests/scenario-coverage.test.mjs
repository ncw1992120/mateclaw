import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const html = await readFile(new URL('index.html', root), 'utf8');
const script = await readFile(new URL('workbench.js', root), 'utf8');

const registered = [...script.matchAll(/scene\('([0-9]{2})'/g)].map(([, id]) => Number(id));
const expectedIds = Array.from({ length: 58 }, (_, index) => index + 1);

test('registers every acceptance scenario from AC-01 through AC-58', () => {
  assert.deepEqual(registered, expectedIds);
});

test('each scene carries a chapter and an explicit expected outcome', () => {
  assert.match(script, /chapter\s*:/, 'scene registry should expose chapter metadata');
  assert.match(script, /expected\s*:/, 'scene registry should expose an expected outcome');
  assert.match(script, /data-expected-result/, 'scenario playback should render its expected outcome');
});

test('workbench exposes pause, take-over, and resume controls', () => {
  assert.match(html, /id="pauseDemo"/, 'pause control is missing');
  assert.match(html, /id="takeOver"/, 'take-over control is missing');
  assert.match(html, /id="resumeDemo"/, 'resume control is missing');
});

test('mainline progress and catalog include all 58 scenarios', () => {
  assert.match(html, /全部 58 个场景/, 'catalog count is stale');
  assert.match(script, /const mainFlow=\['01','02','03','04','05','27','28','29'/, 'edit and preview scenarios should follow initial design before data cases');
  assert.match(script, /length:29/, 'mainline should include the supplemental AC-30 through AC-58 cases');
  assert.match(script, /data-chapter/, 'catalog needs chapter navigation');
});
