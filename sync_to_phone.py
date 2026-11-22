import os
import json
import sqlite3
import subprocess
import time

ALL_DOCS_PATH = "/data/user/0/com.termux/files/home/projects/Apex-Solar-Work/all_35_documents.json"
IMG_SRC_DIR = "/data/user/0/com.termux/files/home/.gemini/antigravity-cli/brain/b5392a9c-4b82-4ac5-8827-ed9911ecf4b5/.user_uploaded"
SDCARD_STAGE_DIR = "/sdcard/Download/apex_photos"

def run_cmd(cmd):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return p.returncode, p.stdout.strip(), p.stderr.strip()

def main():
    with open(ALL_DOCS_PATH) as f:
        docs = json.load(f)

    print(f"Loaded {len(docs)} documents.")

    # 1. Prepare stage directory in /sdcard/Download/apex_photos
    os.makedirs(SDCARD_STAGE_DIR, exist_ok=True)
    for doc in docs:
        src = os.path.join(IMG_SRC_DIR, doc["file"])
        dest = os.path.join(SDCARD_STAGE_DIR, f"{doc['id']}.jpg")
        if os.path.exists(src):
            subprocess.run(["cp", src, dest])

    print("Staged photos to /sdcard/Download/apex_photos.")

    # 2. Copy photos into app internal files/docfolio/ directory using run-as
    run_cmd("adb shell 'run-as com.aistudio.docfolio.krmvqx mkdir -p files/docfolio'")
    run_cmd(f"adb shell 'run-as com.aistudio.docfolio.krmvqx cp {SDCARD_STAGE_DIR}/*.jpg files/docfolio/'")
    run_cmd("adb shell 'run-as com.aistudio.docfolio.krmvqx chmod 600 files/docfolio/*'")

    rc, out, err = run_cmd("adb shell 'run-as com.aistudio.docfolio.krmvqx ls -la files/docfolio/ | wc -l'")
    print(f"Files in app files/docfolio: {out}")

    # 3. Generate SQL insert script
    now_millis = int(time.time() * 1000)
    sql_statements = []

    for doc in docs:
        doc_id = doc["id"]
        user_id = "apex_company"
        file_name = f"{doc_id}.jpg"
        title = doc["title"].replace("'", "''")
        category = doc["category"]
        documentDateIso = doc["documentDateIso"]
        filedDateIso = doc["filedDateIso"]
        referenceNumber = doc["referenceNumber"].replace("'", "''")
        organization = doc["organization"].replace("'", "''")
        contractorOrParty = doc["contractorOrParty"].replace("'", "''")
        summary = doc["summary"].replace("'", "''")
        extractedFieldsText = "\n".join(doc["extractedFields"]).replace("'", "''")
        localImagePath = f"/data/user/0/com.aistudio.docfolio.krmvqx/files/docfolio/{doc_id}.jpg"
        localPdfPath = ""
        ocrContent = extractedFieldsText

        stmt = f"""INSERT OR REPLACE INTO documents (id, userId, fileName, title, category, documentDateIso, filedDateIso, referenceNumber, organization, contractorOrParty, summary, extractedFieldsText, localImagePath, localPdfPath, ocrContent, updatedAtMillis) VALUES ('{doc_id}', '{user_id}', '{file_name}', '{title}', '{category}', '{documentDateIso}', '{filedDateIso}', '{referenceNumber}', '{organization}', '{contractorOrParty}', '{summary}', '{extractedFieldsText}', '{localImagePath}', '{localPdfPath}', '{ocrContent}', {now_millis});"""
        sql_statements.append(stmt)

    all_sql = "\n".join(sql_statements)
    sql_file = "/sdcard/Download/populate_docs.sql"
    with open(sql_file, "w") as f:
        f.write(all_sql)

    print("SQL script written to /sdcard/Download/populate_docs.sql")

    # 4. Execute SQL against app database
    rc, out, err = run_cmd(f"adb shell 'run-as com.aistudio.docfolio.krmvqx sqlite3 databases/docfolio_local_cache.db < {sql_file}'")
    print(f"Database update result: rc={rc}, out={out}, err={err}")

    # 5. Verify row count
    rc, out, err = run_cmd("adb shell 'run-as com.aistudio.docfolio.krmvqx sqlite3 databases/docfolio_local_cache.db \"SELECT COUNT(*) FROM documents;\"'")
    print(f"Total documents in app database: {out}")

if __name__ == "__main__":
    main()
