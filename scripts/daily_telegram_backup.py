#!/usr/bin/env python3
"""
Daily full backup of Apex Solar Work (Firestore + Oracle Relay / seed media)
into the private Telegram channel (@Apexsolarworkbot) via the Oracle Relay API.

Zero third-party dependencies (Python stdlib + system curl for TLS compatibility).
Stores immutable dated archives (`apex_backup_*_YYYY_MM_DD`) so accidental deletions
inside the Android app never remove backed-up Telegram channel snapshots.
"""

from __future__ import annotations

import argparse
import base64
import csv
import datetime as dt
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from typing import Any

MAX_PART_BYTES = 17 * 1024 * 1024  # Keep under Telegram Bot 20 MB getFile restore cap
EXPENSES_DELIM = "=== APEX_EXPENSES_DATA_V1 ==="
MATERIALS_DELIM = "=== APEX_MATERIALS_DATA_V1 ==="
DATA_END_DELIM = "=== END_APEX_DATA ==="
SITES_DELIM = "=== APEX_SITE_LOCATIONS_V1 ==="
SITES_END_DELIM = "=== END_APEX_SITE_LOCATIONS ==="


def load_project_config(repo_root: Path) -> dict[str, str]:
    gs_path = repo_root / "app" / "google-services.json"
    cfg_xml_path = repo_root / "app" / "src" / "main" / "res" / "values" / "firebase_applet_config.xml"
    relay_kt_path = repo_root / "app" / "src" / "main" / "java" / "com" / "example" / "data" / "MediaRelayService.kt"

    gs = json.loads(gs_path.read_text(encoding="utf-8"))
    project_id = gs["project_info"]["project_id"]
    api_key = gs["client"][0]["api_key"][0]["current_key"]

    xml_text = cfg_xml_path.read_text(encoding="utf-8")
    db_match = re.search(r'name="firestore_database_id"[^>]*>([^<]+)<', xml_text)
    if not db_match:
        raise RuntimeError("Could not parse firestore_database_id from firebase_applet_config.xml")
    db_id = db_match.group(1).strip()

    relay_text = relay_kt_path.read_text(encoding="utf-8")
    url_match = re.search(r'BASE_URL\s*=\s*"([^"]+)"', relay_text)
    tok_match = re.search(r'RELAY_INTERNAL_SECRET\s*=\s*"([^"]+)"', relay_text)
    relay_url = os.environ.get("APEX_RELAY_URL") or (url_match.group(1) if url_match else "")
    relay_token = os.environ.get("APEX_RELAY_TOKEN") or (tok_match.group(1) if tok_match else "")
    if not relay_url or not relay_token:
        raise RuntimeError("Missing relay URL or token configuration")

    return {
        "project_id": project_id,
        "api_key": api_key,
        "db_id": db_id,
        "relay_url": relay_url.rstrip("/"),
        "relay_token": relay_token,
    }


def decode_firestore_value(val: dict[str, Any]) -> Any:
    if "stringValue" in val:
        return val["stringValue"]
    if "integerValue" in val:
        return int(val["integerValue"])
    if "doubleValue" in val:
        return float(val["doubleValue"])
    if "booleanValue" in val:
        return bool(val["booleanValue"])
    if "timestampValue" in val:
        return val["timestampValue"]
    if "nullValue" in val:
        return None
    if "arrayValue" in val:
        return [decode_firestore_value(v) for v in val["arrayValue"].get("values", [])]
    if "mapValue" in val:
        return {k: decode_firestore_value(v) for k, v in val["mapValue"].get("fields", {}).items()}
    return val


def fetch_all_company_documents(cfg: dict[str, str]) -> list[dict[str, Any]]:
    url = (
        f"https://firestore.googleapis.com/v1/projects/{cfg['project_id']}"
        f"/databases/{cfg['db_id']}/documents:runQuery?key={cfg['api_key']}"
    )
    all_docs: list[dict[str, Any]] = []
    last_name: str | None = None
    batch_size = 200

    while True:
        structured_query: dict[str, Any] = {
            "from": [{"collectionId": "company_documents"}],
            "orderBy": [{"field": {"fieldPath": "__name__"}, "direction": "ASCENDING"}],
            "limit": batch_size,
        }
        if last_name:
            structured_query["startAt"] = {
                "values": [{"referenceValue": last_name}],
                "before": False,
            }

        payload = json.dumps({"structuredQuery": structured_query}).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            rows = json.loads(resp.read().decode("utf-8"))

        batch_docs = [row["document"] for row in rows if "document" in row]
        if not batch_docs:
            break

        for d in batch_docs:
            doc_id = d["name"].split("/")[-1]
            parsed = {k: decode_firestore_value(v) for k, v in d.get("fields", {}).items()}
            parsed["_docId"] = doc_id
            parsed["_resourceName"] = d["name"]
            all_docs.append(parsed)

        if len(batch_docs) < batch_size:
            break
        last_name = batch_docs[-1]["name"]

    return all_docs


def parse_between(text: str, start_marker: str, end_markers: list[str]) -> str:
    if start_marker not in text:
        return ""
    start_idx = text.index(start_marker) + len(start_marker)
    candidates = [text.index(m, start_idx) for m in end_markers if m in text[start_idx:]]
    end_idx = min(candidates) if candidates else len(text)
    return text[start_idx:end_idx].strip()


def extract_expenses_materials_and_sites(
    docs: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    expenses: list[dict[str, Any]] = []
    materials: list[dict[str, Any]] = []
    sites: list[dict[str, Any]] = []

    for doc in docs:
        ocr = str(doc.get("ocrFullText", ""))
        date_iso = str(doc.get("documentDateIso") or doc.get("filedDateIso") or "")

        exp_json = parse_between(ocr, EXPENSES_DELIM, [MATERIALS_DELIM, DATA_END_DELIM])
        if exp_json:
            try:
                parsed = json.loads(exp_json)
                if isinstance(parsed, list):
                    for item in parsed:
                        if isinstance(item, dict):
                            item.setdefault("dateIso", date_iso)
                            item["_sourceDocId"] = doc["_docId"]
                            expenses.append(item)
            except json.JSONDecodeError:
                pass

        mat_json = parse_between(ocr, MATERIALS_DELIM, [DATA_END_DELIM])
        if mat_json:
            try:
                parsed = json.loads(mat_json)
                if isinstance(parsed, list):
                    for item in parsed:
                        if isinstance(item, dict):
                            item.setdefault("dateIso", date_iso)
                            item["_sourceDocId"] = doc["_docId"]
                            materials.append(item)
            except json.JSONDecodeError:
                pass

        sites_json = parse_between(ocr, SITES_DELIM, [SITES_END_DELIM])
        if sites_json:
            try:
                parsed = json.loads(sites_json)
                if isinstance(parsed, list):
                    for item in parsed:
                        if isinstance(item, dict):
                            sites.append(item)
            except json.JSONDecodeError:
                pass

    return expenses, materials, sites


def build_human_readable_report(
    backup_time_utc: str,
    docs: list[dict[str, Any]],
    expenses: list[dict[str, Any]],
    materials: list[dict[str, Any]],
    sites: list[dict[str, Any]],
) -> str:
    exp_docs = [
        d for d in docs
        if d["_docId"].startswith("daily_expenses_")
        or d["_docId"].startswith("expense_report_")
        or EXPENSES_DELIM in str(d.get("ocrFullText", ""))
    ]
    site_docs = [
        d for d in docs
        if d["_docId"] == "site_locations_master"
        or d["_docId"].startswith("site_locations_")
        or SITES_DELIM in str(d.get("ocrFullText", ""))
    ]
    hidden_ids = {d["_docId"] for d in exp_docs} | {d["_docId"] for d in site_docs}
    vault_docs = [d for d in docs if d["_docId"] not in hidden_ids]

    lines = [
        "================================================================",
        "APEX SOLAR WORK — FULL CLOUD & TELEGRAM DISASTER RECOVERY BACKUP",
        f"Backup UTC Time : {backup_time_utc}",
        f"Total Cloud Docs: {len(docs)} (Vault={len(vault_docs)}, ExpenseSheets={len(exp_docs)}, SiteSync={len(site_docs)})",
        f"Expense Entries : {len(expenses)}",
        f"Material Entries: {len(materials)}",
        f"Site Locations  : {len(sites)}",
        "================================================================",
        "",
        "--- STORED SITE LOCATIONS & GPS ---",
    ]

    if not sites:
        lines.append("(None saved in cloud yet)")
    for idx, s in enumerate(sites, 1):
        lat = s.get("latitude")
        lng = s.get("longitude")
        gps = f"{lat:.6f}, {lng:.6f}" if isinstance(lat, (int, float)) and isinstance(lng, (int, float)) else ""
        lines.append(f"{idx}. {s.get('name', '')} [{s.get('siteType', '')}]")
        if s.get("addressOrVillage"):
            lines.append(f"   Address: {s['addressOrVillage']}")
        if gps:
            lines.append(f"   GPS: {gps}")
        if s.get("googleMapsUrl"):
            lines.append(f"   Maps: {s['googleMapsUrl']}")
        if s.get("notes"):
            lines.append(f"   Notes: {s['notes']}")

    lines.append("\n--- DAILY KHARCHA SUMMARY BY DATE ---")

    by_date: dict[str, list[dict[str, Any]]] = {}
    for e in expenses:
        by_date.setdefault(str(e.get("dateIso", "")), []).append(e)

    for day in sorted(by_date.keys(), reverse=True):
        day_items = by_date[day]
        day_total = sum(float(x.get("amount", 0) or 0) for x in day_items)
        lines.append(f"\n[{day}] Total: Rs {day_total:.0f} ({len(day_items)} items)")
        for x in day_items:
            qty = f" ({x['quantityNote']})" if x.get("quantityNote") else ""
            payer = x.get("paidBy") or "Aziz"
            lines.append(f"  - {x.get('title', '')}{qty}: Rs {float(x.get('amount', 0) or 0):.0f} [{payer}]")

    lines.append("\n--- SITE MATERIALS IN / OUT ---")
    mat_by_date: dict[str, list[dict[str, Any]]] = {}
    for m in materials:
        mat_by_date.setdefault(str(m.get("dateIso", "")), []).append(m)
    for day in sorted(mat_by_date.keys(), reverse=True):
        lines.append(f"\n[{day}]")
        for m in mat_by_date[day]:
            lines.append(
                f"  {m.get('sNo', '-')}. {m.get('itemName', '')} | OUT: {m.get('qtyOut', '')} | IN STORE: {m.get('qtyInStore', '')}"
            )

    lines.append("\n--- VAULT DOCUMENTS (GATE PASSES, SLIPS, PROTOCOLS) ---")
    for d in sorted(vault_docs, key=lambda x: (str(x.get("filedDateIso", "")), x["_docId"]), reverse=True):
        lines.append(
            f"- {d.get('filedDateIso', '')} | {d['_docId']} | {d.get('category', '')} | "
            f"Ref#{d.get('referenceNumber', '')} | {d.get('title', '')}"
        )
        lines.append(f"  Summary: {d.get('summary', '')}")
        for field in d.get("extractedFields", []) or []:
            lines.append(f"    * {field}")

    return "\n".join(lines) + "\n"


def curl_download_if_exists(url: str, dest: Path) -> bool:
    tmp = dest.with_suffix(dest.suffix + ".tmp")
    cmd = ["curl", "-s", "-k", "-f", "-o", str(tmp), url]
    res = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if res.returncode == 0 and tmp.exists() and tmp.stat().st_size > 0:
        tmp.replace(dest)
        return True
    if tmp.exists():
        tmp.unlink(missing_ok=True)
    return False


def collect_media_files(
    cfg: dict[str, str],
    repo_root: Path,
    docs: list[dict[str, Any]],
    media_dir: Path,
) -> tuple[list[Path], list[str]]:
    media_dir.mkdir(parents=True, exist_ok=True)
    seed_dir = repo_root / "app" / "src" / "main" / "assets" / "seed_photos"
    seed_files = {p.name: p for p in seed_dir.glob("*.jpg")} if seed_dir.exists() else {}

    collected: list[Path] = []
    notes: list[str] = []

    for d in docs:
        doc_id = d["_docId"]
        local_img_name = str(d.get("localImageFileName", "")).strip()
        local_pdf_name = str(d.get("localPdfFileName", "")).strip()

        # 1. Resolve photo (seed_photos -> Oracle relay -> previewBase64 fallback)
        photo_out = media_dir / f"{doc_id}.jpg"
        got_photo = False
        if f"{doc_id}.jpg" in seed_files:
            shutil.copy2(seed_files[f"{doc_id}.jpg"], photo_out)
            got_photo = True
        elif local_img_name and local_img_name in seed_files:
            shutil.copy2(seed_files[local_img_name], photo_out)
            got_photo = True
        else:
            relay_photo_url = f"{cfg['relay_url']}/api/documents/{doc_id}/photo?token={cfg['relay_token']}"
            if curl_download_if_exists(relay_photo_url, photo_out):
                got_photo = True
            else:
                b64 = str(d.get("previewBase64", "")).strip()
                if b64:
                    try:
                        raw = base64.b64decode(b64)
                        if len(raw) > 200:
                            photo_out = media_dir / f"{doc_id}_preview.jpg"
                            photo_out.write_bytes(raw)
                            got_photo = True
                    except Exception:
                        pass

        if got_photo and photo_out.exists():
            collected.append(photo_out)
        elif local_img_name:
            notes.append(f"photo_missing:{doc_id}")

        # 2. Fetch PDF if uploaded to Oracle Relay
        if local_pdf_name or not doc_id.startswith("daily_expenses_"):
            pdf_out = media_dir / f"{doc_id}.pdf"
            relay_pdf_url = f"{cfg['relay_url']}/api/documents/{doc_id}/pdf?token={cfg['relay_token']}"
            if curl_download_if_exists(relay_pdf_url, pdf_out):
                collected.append(pdf_out)

    return collected, notes


def write_split_media_zips(media_files: list[Path], out_dir: Path, date_tag: str) -> list[Path]:
    """Packs media into ZIP parts each <= MAX_PART_BYTES so Bot API can both upload and re-download."""
    if not media_files:
        return []

    parts: list[Path] = []
    part_idx = 1
    current_path = out_dir / f"apex_backup_media_{date_tag}_p{part_idx}.zip"
    zf = zipfile.ZipFile(current_path, "w", compression=zipfile.ZIP_STORED)

    try:
        for mf in sorted(media_files, key=lambda p: p.name):
            fsize = mf.stat().st_size
            cur_size = current_path.stat().st_size if current_path.exists() else 0
            # Start new ZIP part before crossing MAX_PART_BYTES (with overhead margin)
            if zf.filelist and (cur_size + fsize + 4096) > MAX_PART_BYTES:
                zf.close()
                parts.append(current_path)
                part_idx += 1
                current_path = out_dir / f"apex_backup_media_{date_tag}_p{part_idx}.zip"
                zf = zipfile.ZipFile(current_path, "w", compression=zipfile.ZIP_STORED)
            zf.write(mf, arcname=f"media/{mf.name}")
    finally:
        zf.close()

    if current_path.exists() and current_path.stat().st_size > 22:
        parts.append(current_path)
    return parts


def upload_to_telegram_relay(cfg: dict[str, str], doc_id: str, file_path: Path) -> dict[str, Any]:
    upload_url = f"{cfg['relay_url']}/api/documents/{doc_id}/upload?token={cfg['relay_token']}"
    cmd = [
        "curl", "-s", "-k", "-f", "-X", "POST",
        "-F", f"file=@{file_path}",
        "-F", "file_type=pdf",
        upload_url,
    ]
    out = subprocess.check_output(cmd, timeout=120).decode("utf-8").strip()
    parsed = json.loads(out)
    if parsed.get("status") != "success":
        raise RuntimeError(f"Unexpected upload response for {doc_id}: {out}")

    # Verify HTTP 200 GET from relay (confirms Telegram file_id resolves cleanly)
    verify_url = f"{cfg['relay_url']}/api/documents/{doc_id}/pdf?token={cfg['relay_token']}"
    verify_out = subprocess.check_output(
        ["curl", "-s", "-k", "-o", "/dev/null", "-w", "%{http_code} %{size_download}", verify_url],
        timeout=60,
    ).decode("utf-8").strip()
    code_str, sz_str = verify_out.split()
    if code_str != "200" or int(sz_str) != file_path.stat().st_size:
        raise RuntimeError(
            f"Verification mismatch for {doc_id}: expected 200/{file_path.stat().st_size}, got {verify_out}"
        )
    parsed["verified_get"] = verify_out
    return parsed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Daily Apex Solar Work Telegram backup")
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Use ephemeral temp staging and print only concise count summary (safe for CI logs)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parent.parent
    cfg = load_project_config(repo_root)

    now_utc = dt.datetime.now(dt.timezone.utc)
    date_tag = now_utc.strftime("%Y_%m_%d")
    backup_time_str = now_utc.strftime("%Y-%m-%dT%H:%M:%SZ")

    ci_mode = args.quiet or os.environ.get("GITHUB_ACTIONS") == "true"
    tmp_ctx: tempfile.TemporaryDirectory[str] | None = None

    if ci_mode:
        tmp_ctx = tempfile.TemporaryDirectory(prefix=f"apex_backup_{date_tag}_")
        run_dir = Path(tmp_ctx.name)
        scratch_backups = None
    else:
        scratch_backups = (
            Path.home()
            / ".gemini"
            / "antigravity-cli"
            / "brain"
            / "b5392a9c-4b82-4ac5-8827-ed9911ecf4b5"
            / "scratch"
            / "backups"
        )
        run_dir = scratch_backups / f"run_{date_tag}"
        if run_dir.exists():
            shutil.rmtree(run_dir)
        run_dir.mkdir(parents=True, exist_ok=True)

    try:
        media_dir = run_dir / "media"

        docs = fetch_all_company_documents(cfg)
        expenses, materials, sites = extract_expenses_materials_and_sites(docs)
        report_txt = build_human_readable_report(backup_time_str, docs, expenses, materials, sites)

        # Write structured data files
        full_json_path = run_dir / f"apex_firestore_backup_{date_tag}.json"
        full_json_path.write_text(
            json.dumps(
                {
                    "backupTimeUtc": backup_time_str,
                    "projectId": cfg["project_id"],
                    "databaseId": cfg["db_id"],
                    "documentCount": len(docs),
                    "expenseCount": len(expenses),
                    "materialCount": len(materials),
                    "siteLocationCount": len(sites),
                    "documents": docs,
                    "extractedExpenses": expenses,
                    "extractedMaterials": materials,
                    "extractedSiteLocations": sites,
                },
                indent=2,
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )

        report_path = run_dir / f"apex_readable_report_{date_tag}.txt"
        report_path.write_text(report_txt, encoding="utf-8")

        csv_path = run_dir / f"apex_expenses_{date_tag}.csv"
        with csv_path.open("w", newline="", encoding="utf-8") as cf:
            writer = csv.writer(cf)
            writer.writerow(["dateIso", "id", "title", "amount", "paidBy", "category", "quantityNote", "sourceDocId"])
            for e in sorted(expenses, key=lambda x: (str(x.get("dateIso", "")), str(x.get("id", "")))):
                writer.writerow([
                    e.get("dateIso", ""),
                    e.get("id", ""),
                    e.get("title", ""),
                    e.get("amount", 0),
                    e.get("paidBy", ""),
                    e.get("category", ""),
                    e.get("quantityNote", ""),
                    e.get("_sourceDocId", ""),
                ])

        # Create compact Data + Project Config ZIP
        data_zip_path = run_dir / f"apex_backup_data_{date_tag}.zip"
        with zipfile.ZipFile(data_zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            zf.write(full_json_path, arcname=full_json_path.name)
            zf.write(report_path, arcname=report_path.name)
            zf.write(csv_path, arcname=csv_path.name)
            for rel in ("firestore.rules", "app-version.json", "app/src/main/assets/seed_documents.json"):
                src = repo_root / rel
                if src.exists():
                    zf.write(src, arcname=f"config/{src.name}")

        # Collect & split full-res media ZIPs
        media_files, notes = collect_media_files(cfg, repo_root, docs, media_dir)
        media_zips = write_split_media_zips(media_files, run_dir, date_tag)

        uploads: list[dict[str, Any]] = []
        # Upload readable summary TXT + full data ZIP + media ZIP parts to Telegram channel
        uploads.append(upload_to_telegram_relay(cfg, f"apex_backup_report_{date_tag}", report_path))
        uploads.append(upload_to_telegram_relay(cfg, f"apex_backup_data_{date_tag}", data_zip_path))
        for idx, mz in enumerate(media_zips, 1):
            uploads.append(upload_to_telegram_relay(cfg, f"apex_backup_media_{date_tag}_p{idx}", mz))

        status = {
            "backupTimeUtc": backup_time_str,
            "dateTag": date_tag,
            "docsBackedUp": len(docs),
            "expensesBackedUp": len(expenses),
            "materialsBackedUp": len(materials),
            "sitesBackedUp": len(sites),
            "mediaFilesCount": len(media_files),
            "notes": notes,
            "uploads": uploads,
        }

        if scratch_backups is not None:
            (scratch_backups / "last_backup_status.json").write_text(
                json.dumps(status, indent=2), encoding="utf-8"
            )
            # Prune local scratch backup archives older than 3 days (Telegram channel retains everything)
            cutoff = now_utc - dt.timedelta(days=3)
            for child in scratch_backups.glob("run_*"):
                if child.is_dir() and child != run_dir:
                    try:
                        day_dt = dt.datetime.strptime(child.name.removeprefix("run_"), "%Y_%m_%d").replace(tzinfo=dt.timezone.utc)
                        if day_dt < cutoff:
                            shutil.rmtree(child, ignore_errors=True)
                    except ValueError:
                        pass

        if args.quiet:
            msg_ids = ",".join(str(u.get("telegram_message_id", "?")) for u in uploads)
            print(
                f"Backup OK ({date_tag}): docs={len(docs)} expenses={len(expenses)} "
                f"materials={len(materials)} sites={len(sites)} media={len(media_files)} uploads={len(uploads)} msgs={msg_ids}"
            )
        else:
            print(json.dumps(status, indent=2))
        return 0
    finally:
        if tmp_ctx is not None:
            tmp_ctx.cleanup()


if __name__ == "__main__":
    sys.exit(main())
