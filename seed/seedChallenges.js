const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

admin.initializeApp({
  credential: admin.credential.cert(require(path.join(__dirname, "serviceAccountKey.json"))),
});

const db = admin.firestore();

function slugify(s) {
  return String(s).trim().toLowerCase()
    .replace(/['"]/g, "")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 80);
}

async function main() {
  const items = JSON.parse(fs.readFileSync(path.join(__dirname, "challenges.json"), "utf8"));
  if (!Array.isArray(items)) throw new Error("challenges.json must be a JSON array");

  const now = admin.firestore.FieldValue.serverTimestamp();

  let batch = db.batch();
  let ops = 0;
  let written = 0;

  for (let i = 0; i < items.length; i++) {
    const c = items[i];
    const name = c.name?.trim();
    const description = c.description?.trim();
    const difficulty = (c.difficulty || "").toLowerCase();
    const hashtag = (c.hashtag || "").toLowerCase().trim();
    const points = Number(c.points ?? 10);

    if (!name || !description || !hashtag) throw new Error(`Missing fields at index ${i}`);
    if (!["easy", "medium", "hard"].includes(difficulty)) throw new Error(`Bad difficulty at index ${i}`);
    if (points !== 10) throw new Error(`Points must be 10 at index ${i}`);

    const id = `${difficulty}_${hashtag}_${slugify(name)}`;

    batch.set(
      db.collection("challenges").doc(id),
      { name, description, difficulty, hashtag, points: 10, active: true, createdAt: now },
      { merge: true } // safe to re-run
    );

    ops++; written++;
    if (ops >= 450) { await batch.commit(); batch = db.batch(); ops = 0; }
  }
  if (ops > 0) await batch.commit();

  console.log(`Done. Upserted ${written} challenges into challenges collection.`);
}

main().catch(e => { console.error(e); process.exit(1); });