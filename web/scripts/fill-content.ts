import * as fs from "fs";
import * as path from "path";

const GENERATED_DIR = path.resolve(__dirname, "..", "src", "data", "generated");

function main() {
  const docsPath = path.join(GENERATED_DIR, "docs.json");

  if (!fs.existsSync(docsPath)) {
    console.error(`Error: docs.json not found at ${docsPath}`);
    process.exit(1);
  }

  console.log(`Reading docs.json from ${docsPath}...`);
  const docs = JSON.parse(fs.readFileSync(docsPath, "utf-8"));

  const processedDocs: any[] = [];
  let replacedCount = 0;

  for (const doc of docs) {
    let processedContent = doc.content;

    if (typeof processedContent === "string" && processedContent.match(/^\w+\.md$/)) {
      const filename = processedContent;

      const match = filename.match(/^s(\d+)(a|b|c)?\.md$/);
      if (match) {
        const numStr = match[1];
        const suffix = match[2] || "";
        const num = parseInt(numStr, 10);
        const mappedFilename = `s${num}${suffix}.md`;
        const sourcePath = path.join(GENERATED_DIR, mappedFilename);

        if (fs.existsSync(sourcePath)) {
          processedContent = fs.readFileSync(sourcePath, "utf-8");
          replacedCount++;
          console.log(`  Replaced content for ${doc.version}/${doc.locale}: ${filename} -> ${mappedFilename}`);
        } else {
          console.warn(`  Warning: Source file not found: ${sourcePath}`);
        }
      }
    }

    processedDocs.push({
      ...doc,
      content: processedContent,
    });
  }

  if (replacedCount > 0) {
    console.log(`\nReplaced ${replacedCount} file references with actual content`);
  } else {
    console.log(`\nNo file references found to replace`);
  }

  fs.writeFileSync(docsPath, JSON.stringify(processedDocs, null, 2));
  console.log(`\nWrote updated docs.json to ${docsPath}`);
}

main();
