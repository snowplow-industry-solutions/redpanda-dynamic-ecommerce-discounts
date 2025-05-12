const fs = require("fs");
const path = require("path");
const Ajv = require("ajv");
const addFormats = require("ajv-formats");

const IGLU_SERVER_URL = process.env.IGLU_SERVER_URL || "http://localhost:8180";
const IGLU_SUPER_API_KEY =
  process.env.IGLU_SUPER_API_KEY || "48b267d7-cd2b-4f22-bae4-0f002008b5ad";
const SCHEMA_PATH =
  process.env.SCHEMA_PATH ||
  "com.snowplow/shopper_discount_applied/jsonschema/1-0-0";

const schemaUrl = `${IGLU_SERVER_URL}/api/schemas/${SCHEMA_PATH.replace(
  /\//g,
  "/"
)}`;

const events = [
  path.join(__dirname, "../discounts-processor/e2e/data/MultiProduct.json"),
  path.join(
    __dirname,
    "../discounts-processor/e2e/data/MostViewedMultipleViewsPerProduct.json"
  ),
];

async function main() {
  let schema;
  try {
    const res = await fetch(schemaUrl, {
      headers: { apikey: IGLU_SUPER_API_KEY },
    });
    if (!res.ok) {
      throw new Error(
        `Failed to fetch schema: ${res.status} ${res.statusText}`
      );
    }
    schema = await res.json();
    console.log(`Schema fetched from: ${schemaUrl}`);
  } catch (err) {
    console.error("Error fetching schema from Iglu Server:", err.message);
    process.exit(1);
  }

  const ajv = new Ajv({
    allErrors: true,
    validateSchema: false,
    strict: false,
    formats: "full",
  });
  addFormats(ajv);
  const validate = ajv.compile(schema);

  let allValid = true;

  for (const eventPath of events) {
    const event = JSON.parse(fs.readFileSync(eventPath, "utf-8"));
    const valid = validate(event);
    if (valid) {
      console.log(`OK: ${path.basename(eventPath)} is valid.`);
    } else {
      allValid = false;
      console.log(`ERROR: ${path.basename(eventPath)} is invalid:`);
      console.log(validate.errors);
    }
  }

  if (!allValid) {
    process.exit(1);
  }
}

main();
