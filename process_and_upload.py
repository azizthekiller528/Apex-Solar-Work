import os
import sys
import json
import time
import requests
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

RELAY_URL_TEMPLATE = "https://docfolio.130.210.30.151.sslip.io/api/documents/{doc_id}/upload?token=docfolio_secret_test_key_99812"

IMG_DIR = "/data/user/0/com.termux/files/home/.gemini/antigravity-cli/brain/b5392a9c-4b82-4ac5-8827-ed9911ecf4b5/.user_uploaded"

DOCUMENTS = [
    {
        "id": "apex_doc_01_slip_13703",
        "file": "media_1795368684635_0d75d701.jpg",
        "title": "Material Slip 13703 - Solar Plant 287.2kW (Hajipur)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "13703",
        "documentDateIso": "2026-04-18",
        "filedDateIso": "2026-04-18",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar",
        "department": "MEP",
        "summary": "Requisition for Solar Plant 287.2kW at Hajipur-Syana. Columns, Rafters, Purlins, and Bracings.",
        "extractedFields": [
            "Doc No: FCN-ST-13 (R1)",
            "Slip No: 13703",
            "Date: 18.04.26",
            "To: Hajipur - Syana Stores",
            "Sub Contractor: Apex Solar",
            "Dept: MEP",
            "Solar Plant 287.2kW",
            "Column (3350mm): 14 Nos",
            "Rafter (3342): 14 Nos",
            "Purlin (3697): 08 Nos",
            "Purlin (2950): 08 Nos",
            "Bracing (1467): 14 Nos",
            "Bracing (1424): 14 Nos",
            "Bracing (3180): 04 Nos",
            "Requisition by: Devanshu"
        ]
    },
    {
        "id": "apex_doc_02_slip_13726",
        "file": "media_1795368684720_dafea23e.jpg",
        "title": "Material Slip 13726 - Anti Theft Nut Bolt (Hajipur)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "13726",
        "documentDateIso": "2026-04-27",
        "filedDateIso": "2026-04-27",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar",
        "department": "MEP",
        "summary": "Requisition of 300 Anti theft Nut Bolts at Hajipur Stores Syana.",
        "extractedFields": [
            "Slip No: 13726",
            "Date: 27.04.26",
            "To: Hajipur Stores Syana",
            "Sub Contractor: Apex Solar",
            "Dept: MEP",
            "Issue Type: SC / VC transfer",
            "Anti theft Nut Bolt: Req 300, Issued 300 Nos (Three Hundred)",
            "Requisition by: Devanshu",
            "Received by: ACA"
        ]
    },
    {
        "id": "apex_doc_03_slip_13710",
        "file": "media_1795368684779_b22c3015.jpg",
        "title": "Material Slip 13710 - M10 Bolt & Clit (Hajipur)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "13710",
        "documentDateIso": "2026-04-21",
        "filedDateIso": "2026-04-21",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar Works",
        "department": "MEP",
        "summary": "Requisition of M10 Bolts, Clit, and Column at Hajipur Stores.",
        "extractedFields": [
            "Slip No: 13710",
            "Date: 21.04.26",
            "To: Hajipur Stores Syana",
            "Sub Contractor: Apex Solar Works",
            "Dept: MEP",
            "M10 Bolt: 565 Nos",
            "Clit (95x70x4): 96 Nos",
            "Column (3350): 01 Nos"
        ]
    },
    {
        "id": "apex_doc_04_slip_13705",
        "file": "media_1795368684821_5bde0af0.jpg",
        "title": "Material Slip 13705 - Cement 20 Bags (Hajipur)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "13705",
        "documentDateIso": "2026-04-18",
        "filedDateIso": "2026-04-18",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar",
        "department": "MEP",
        "summary": "Requisition of 20 bags Cement on chargeable basis.",
        "extractedFields": [
            "Slip No: 13705",
            "Date: 18.04.26",
            "To: Hajipur Syana Stores",
            "Sub Contractor: Apex Solar",
            "Dept: MEP",
            "Issue Type: Chargeable basis",
            "Cement: Req 20, Issued 20 (Twenty Bag)",
            "Requisition by: Devanshu"
        ]
    },
    {
        "id": "apex_doc_05_slip_13704",
        "file": "media_1795368684865_66c82e6a.jpg",
        "title": "Material Slip 13704 - Purlins, Bracing & Clits (Hajipur)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "13704",
        "documentDateIso": "2026-04-18",
        "filedDateIso": "2026-04-18",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar",
        "department": "MEP",
        "summary": "Requisition of Purlins, Bracings, Connection Plates, Clits, Columns, and Rafters.",
        "extractedFields": [
            "Slip No: 13704",
            "Date: 18.04.26",
            "To: Hajipur - Syana Stores",
            "Sub Contractor: Apex Solar",
            "Purlin (4104): 04 Nos",
            "Bracing (3086): 02 Nos",
            "Connection Plate (4mm): 28 Nos",
            "Clit (4mm): 56 Nos",
            "Column (3350): 03 Nos",
            "Purlin (5125): 02 Nos",
            "Rafter (1400): 03 Nos",
            "Bracing (1108): 03 Nos",
            "Connection Plate (4mm): 03 Nos",
            "Requisition by: Devanshu"
        ]
    },
    {
        "id": "apex_doc_06_slip_13717",
        "file": "media_1795368684908_c89e838c.jpg",
        "title": "Material Slip 13717 - Nut Bolt 8mm (440 Nos)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "13717",
        "documentDateIso": "2026-04-24",
        "filedDateIso": "2026-04-24",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar",
        "department": "MEP",
        "summary": "Issue of 440 Nos Nut Bolt 8mm.",
        "extractedFields": [
            "Slip No: 13717",
            "Date: 24/04/2026",
            "Sub Contractor: Apex Solar",
            "Dept: MEP",
            "Nut Bolt 8mm: Req 440, Issued 440",
            "Received by: ACA"
        ]
    },
    {
        "id": "apex_doc_07_slip_14146",
        "file": "media_1795368684946_d661ede3.jpg",
        "title": "Material Slip 14146 - PV Modules 400W (38 Nos)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "14146",
        "documentDateIso": "2026-04-29",
        "filedDateIso": "2026-04-29",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar Works",
        "department": "MEP",
        "summary": "Issue of 38 Solar PV Modules (400W) to Apex Solar Works.",
        "extractedFields": [
            "Slip No: 14146",
            "Date: 29.04.26",
            "To: Hajipur - Syana Stores",
            "Sub Contractor: Apex Solar Works",
            "Dept: MEP",
            "PV Modules (400W): Req 38, Issued 38 Nos (Thirty Eight)",
            "Requisition by: Devanshu",
            "Received by: Dhiraj Kumar"
        ]
    },
    {
        "id": "apex_doc_08_slip_14202",
        "file": "media_1795368684995_46f263a0.jpg",
        "title": "Material Slip 14202 - PV Modules & Electricals (Kharkali)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "14202",
        "documentDateIso": "2026-05-26",
        "filedDateIso": "2026-05-26",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar (5369692)",
        "department": "MEP",
        "summary": "PV Modules, VFD Panel 20HP, DCDBs, Earthing Strip, Spike LA at Kharkali.",
        "extractedFields": [
            "Slip No: 14202",
            "Date: 26.05.26",
            "To: Kharkali - BBN Stores Syana",
            "Sub Contractor: Apex Solar (5369692)",
            "PV Modules (400W): 17 Nos",
            "VFD Panel (20HP): 01 Nos",
            "DCDB (3 in 1): 01 Nos",
            "DCDB (1 in 1 out): 01 Nos",
            "Anti Theft: 100 Nos",
            "Spike LA: 01 Nos",
            "Flexible Pipe: 03 Bundle",
            "Earthing Strip: 90 Mtr",
            "Requisition by: Devanshu",
            "Received by: Dhiraj V."
        ]
    },
    {
        "id": "apex_doc_09_slip_14015",
        "file": "media_1795368685046_515b33eb.jpg",
        "title": "Material Slip 14015 - Structure & Cement (Karimnagar Banboi)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "14015",
        "documentDateIso": "2026-05-30",
        "filedDateIso": "2026-05-30",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar (5369692)",
        "department": "MEP",
        "summary": "Structure steel and cement for Karimnagar Banboi solar project.",
        "extractedFields": [
            "Slip No: 14015",
            "Date: 30.05.26",
            "To: Karimnagar Banboi - BBN Stores Syana",
            "Sub Contractor: Apex Solar (5369692)",
            "Column: 17 Nos",
            "Rafter: 17 Nos",
            "Purlin: 42 Nos",
            "Clit: 68 Nos",
            "Connection Plate: 34 Nos",
            "Cement OPC: 15 Bags",
            "Bracing: 37 Nos",
            "Requisition by: Devanshu",
            "Received by: Dhiraj Kumar"
        ]
    },
    {
        "id": "apex_doc_10_slip_13748",
        "file": "media_1795368685089_d6af5c72.jpg",
        "title": "Material Slip 13748 - 40 PV Modules (Kharkali)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "13748",
        "documentDateIso": "2026-05-13",
        "filedDateIso": "2026-05-13",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar",
        "department": "MEP",
        "summary": "Requisition of 40 Solar PV Modules (400W) at Kharkhali.",
        "extractedFields": [
            "Slip No: 13748",
            "Date: 13.05.26",
            "To: Kharkhali - BBN Stores Syana",
            "Sub Contractor: Apex Solar",
            "Solar PV Modules (400W): Req 40, Issued 40 (Forty Nos)",
            "Requisition by: Devanshu"
        ]
    },
    {
        "id": "apex_doc_11_slip_14025",
        "file": "media_1795368685133_fc003d53.jpg",
        "title": "Material Slip 14025 - PV Modules & Nut Bolts (Karimnagar)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "14025",
        "documentDateIso": "2026-06-09",
        "filedDateIso": "2026-06-09",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar (5369692)",
        "department": "MEP",
        "summary": "PV Modules and Hardware for Karimnagar Banboi.",
        "extractedFields": [
            "Slip No: 14025",
            "Date: 09.06.26",
            "To: Karimnagar Banboi Stores Syana",
            "Sub Contractor: Apex Solar (5369692)",
            "PV Module (400W): 40 Nos",
            "Anti theft nut bolt: 300 Nos",
            "M10 Nut Bolt: 150 Nos",
            "Requisition by: Devanshu",
            "Received by: Dhiraj V."
        ]
    },
    {
        "id": "apex_doc_12_slip_14025_c2",
        "file": "media_1795368685179_2c64c989.jpg",
        "title": "Material Slip 14025 (Copy 2) - PV Modules (Karimnagar)",
        "category": "MATERIAL_SLIP",
        "referenceNumber": "14025-C2",
        "documentDateIso": "2026-06-09",
        "filedDateIso": "2026-06-09",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "Apex Solar (5369692)",
        "department": "MEP",
        "summary": "Slip 14025 alternate copy / store receipt.",
        "extractedFields": [
            "Slip No: 14025",
            "Date: 09.06.26",
            "PV Module (400W): 40 Nos",
            "Anti theft nut bolt: 300 Nos",
            "M10 Nut Bolt: 150 Nos"
        ]
    },
    {
        "id": "apex_doc_13_omfa_purlin",
        "file": "media_1795368685237_f8529be2.jpg",
        "title": "Site Note - Purlin 18 No (260/-)",
        "category": "SITE_REPORT",
        "referenceNumber": "NOTE-OMFA-01",
        "documentDateIso": "2026-05-15",
        "filedDateIso": "2026-05-15",
        "organization": "Site Field Requisition",
        "contractorOrParty": "Apex Solar",
        "department": "Field",
        "summary": "Handwritten site memo on OMFA paper for Purlin quantity & rate.",
        "extractedFields": [
            "Item: Purlin 18 No",
            "Amount: 260/-",
            "Paper: OMFA Premium"
        ]
    },
    {
        "id": "apex_doc_14_german_estimate",
        "file": "media_1795368685290_44975b42.jpg",
        "title": "Estimate Slip - German Lubricants (260/-)",
        "category": "INVOICE_RECEIPT",
        "referenceNumber": "EST-GERMAN-260",
        "documentDateIso": "2026-05-16",
        "filedDateIso": "2026-05-16",
        "organization": "German Lubricants & Greases",
        "contractorOrParty": "Apex Solar",
        "department": "Purchase",
        "summary": "Handwritten estimate slip for 4 Purlin at 260/-.",
        "extractedFields": [
            "Header: GERMAN Technology Innovated - Estimate",
            "Item: 4 Purlin: 260/-",
            "Total: 260/-"
        ]
    },
    {
        "id": "apex_doc_15_purlin_hardware_memo",
        "file": "media_1795368685326_e61b54a2.jpg",
        "title": "Purchase Memo - Purlins & Hardware (1030/-)",
        "category": "INVOICE_RECEIPT",
        "referenceNumber": "MEMO-1030",
        "documentDateIso": "2026-05-18",
        "filedDateIso": "2026-05-18",
        "organization": "Local Hardware Supplier",
        "contractorOrParty": "Apex Solar",
        "department": "Site Ops",
        "summary": "Hardware breakdown note totaling 1030/-.",
        "extractedFields": [
            "3-L Purlin 124: 660/-",
            "11-Nut 10,15,16: 770/-",
            "4-Nut 19: 200/-",
            "Final Total: 1030/-"
        ]
    },
    {
        "id": "apex_doc_16_nut_bolt_slip",
        "file": "media_1795368685358_ddc2f867.jpg",
        "title": "Expense Slip - Nut Bolt (870/-)",
        "category": "INVOICE_RECEIPT",
        "referenceNumber": "EXP-NB-870",
        "documentDateIso": "2026-05-20",
        "filedDateIso": "2026-05-20",
        "organization": "Hardware Store",
        "contractorOrParty": "Apex Solar",
        "department": "Site Ops",
        "summary": "Handwritten site cash slip for Nut Bolts totaling 870/-.",
        "extractedFields": [
            "Item: Nut Bolt",
            "Total Amount: 870/-"
        ]
    },
    {
        "id": "apex_doc_17_amit_agri_bill",
        "file": "media_1795368685406_cdb084a8.jpg",
        "title": "Bill - Amit Agriculture Store (550/-)",
        "category": "INVOICE_RECEIPT",
        "referenceNumber": "BILL-AMIT-550",
        "documentDateIso": "2026-05-22",
        "filedDateIso": "2026-05-22",
        "organization": "Amit Agriculture Store, Siyana",
        "contractorOrParty": "Apex Solar",
        "department": "Plumbing/Piping",
        "summary": "Pipes, elbow, and solvent purchase memo totaling 550/-.",
        "extractedFields": [
            "Store: Amit Agriculture Store (Garh Road, Bugrasi Stand, Siyana)",
            "40mm pipe: 110.00",
            "1.5 inch Elbow: 120.00",
            "Primer/Solvent: 150.00",
            "Total: 550.00",
            "Signed"
        ]
    },
    {
        "id": "apex_doc_18_inspection_protocol",
        "file": "media_1795368685445_c51d6e55.jpg",
        "title": "Inspection Protocol - Karimnagar Banboi Solar System",
        "category": "INSPECTION_PROTOCOL",
        "referenceNumber": "INSP-KARIM-01",
        "documentDateIso": "2026-06-25",
        "filedDateIso": "2026-06-25",
        "organization": "KALPATARU / JAL JEEVAN MISSION",
        "contractorOrParty": "Apex Solar",
        "department": "QA/QC",
        "summary": "Completed site inspection protocol with all civil, structure, and electrical check points marked OK.",
        "extractedFields": [
            "Scheme Name: KARIMNAGAR BANBOI",
            "Foundation, Soil Stata, Backfilling: OK",
            "Column Spacing & Diagonal: OK",
            "Structure (SAIL/JINDAL/TATA Steel): OK",
            "Module Installation (PV POWER TECH): OK",
            "Cabling & GI/HDPE Conduit: OK",
            "Earthing & Chemical Pit: OK",
            "Solar Panel / VFD (MANIFOLD / VINWORTH): OK",
            "Inverter (Statecon): OK",
            "Lightning Arrestor / Spike: OK"
        ]
    },
    {
        "id": "apex_doc_19_gatepass_1000356228",
        "file": "media_1795368685474_a65c2df3.jpg",
        "title": "Gate Pass 1000356228 - PV Modules 33 EA (UP13BT3326)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000356228",
        "documentDateIso": "2026-06-26",
        "filedDateIso": "2026-06-26",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR 14244",
        "department": "Jal Jeevan Mission Meerut-JJMM",
        "summary": "Gate pass for 33 PV Modules, Conduit Pipe, Connectors & Spike LA to Karimnagar Banboi.",
        "extractedFields": [
            "Gate Pass No: 1000356228",
            "Date: 26-06-2026 10:13:25 AM",
            "Vehicle No: UP13BT3326",
            "To: APEX SOLAR 14244",
            "Remarks: KARIMNAGAR BANBOI BLOCK BB NAGAR",
            "PV MODULLE 400W: 33.000 EA",
            "FLEXBLE CONDIUT PIPE: 2.000 EA",
            "CABLE TIE: 3.000 EA",
            "MC4 CONECTOR MALE: 10.000 EA",
            "MC 4 CONCTOR FEMAEL: 10.000 EA",
            "SPIKE LA: 1.000 EA",
            "Total Qty: 59.000",
            "Outward No: 6117, Date: 26/06/2026",
            "Sign: Nishan Singh"
        ]
    },
    {
        "id": "apex_doc_20_gatepass_1000367086",
        "file": "media_1795368685505_655e0d75.jpg",
        "title": "Gate Pass 1000367086 - Solar Structure & 50 Modules",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000367086",
        "documentDateIso": "2026-07-23",
        "filedDateIso": "2026-07-23",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR (5369692)",
        "department": "Jal Jeevan Mission Meerut",
        "summary": "Structure steel and 50 PV Modules for Mau Badarkha Karnwas Sirora Anupshahar Dibai.",
        "extractedFields": [
            "Gate Pass No: 1000367086",
            "Date: 23-07-2026 12:07:42 PM",
            "Vehicle No: UP13BT3326",
            "Remarks: MAU BADARKHA KARNWAS SIRORA ANUPSHAHAR DIBAI MRIS 89/90",
            "COLUM 3350: 12.000 EA",
            "RAFTTER 1400: 12.000 EA",
            "PUERLING 5125: 6.000 EA",
            "BRACING 1108: 12.000 EA",
            "CONNECTION PLAT: 95.000 EA",
            "ANTI THETT NUT BOLT M16: 20.000 EA",
            "MODULE 400W: 50.000 EA",
            "NUT BOLT M12: 4.000 EA",
            "DCDB LIN 04: 5.000 EA",
            "RAFTAR 3350: 12.000 EA",
            "Total Qty: 228.000",
            "Outward No: 6327, Date: 23-07-2026",
            "Sign: Lalit"
        ]
    },
    {
        "id": "apex_doc_21_gatepass_1000366559",
        "file": "media_1795368685535_ca09a42f.jpg",
        "title": "Gate Pass 1000366559 - PV Modules & Cables (Surajpur)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000366559",
        "documentDateIso": "2026-07-22",
        "filedDateIso": "2026-07-22",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR 5369692",
        "department": "MEP ISSUE NO 39",
        "summary": "Gate pass for PV Modules, Cables, DCDB, Chemical Bags for Surajpur Makhaina Block Dibai.",
        "extractedFields": [
            "Gate Pass No: 1000366559",
            "Date: 22-07-2026 10:47:47 AM",
            "Vehicle No: UP16LT1748",
            "Remarks: SURAJPUR MAKHAINA BLOCK DIBAI",
            "PV MODULE 400W: 26.000 EA",
            "DCDB 8IN 1: 1.000 EA",
            "DC CABLE 4 SQMM RED: 100.000 M",
            "DC CABLE 4 SQMM BLACK: 100.000 M",
            "DC CABLE 10 SQMM: 150.000 M",
            "CONDUIT PIPE: 120.000 M",
            "MC4 CONNECTORS: 60.000 EA",
            "CHEMICAL BAG: 5.000 BAG",
            "Total Qty: 562.000",
            "Outward No: 6316, Date: 22-07-2026",
            "Sign: Lalit"
        ]
    },
    {
        "id": "apex_doc_22_gatepass_1000357543",
        "file": "media_1795368685565_d7aabed3.jpg",
        "title": "Gate Pass 1000357543 - Batteries & Inverters (E-Rikshaw)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000357543",
        "documentDateIso": "2026-06-30",
        "filedDateIso": "2026-06-30",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR WORKS 14128",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 4 Batteries (150AH) and 2 Inverters (2300W) transported by E-Rikshaw.",
        "extractedFields": [
            "Gate Pass No: 1000357543",
            "Date: 30-06-2026 10:36:29 AM",
            "Vehicle No: E RIKSHAW",
            "Remarks: KARIM NAGAR BANBOI KHARKHALI BBLOCK BB NAGAR",
            "BATT, LA, 12V, 150AH: 4.000 NOS",
            "INVERTER, UPS, 2300W, 24VDC, 2KVA, 50HZ: 2.000 EA",
            "Total Qty: 6.000",
            "Outward No: 6201, Date: 30-06-2026",
            "Sign: Lalit"
        ]
    },
    {
        "id": "apex_doc_23_gatepass_1000366218",
        "file": "media_1795368685612_b81df2df.jpg",
        "title": "Gate Pass 1000366218 - Structure & Hardware (Surajpur)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000366218",
        "documentDateIso": "2026-07-21",
        "filedDateIso": "2026-07-21",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR (5369692)",
        "department": "MEP ISSUE NO 31 32",
        "summary": "Gate pass for structure steel, purlins, rafter, and hardware for Surajpur Makhaina Dibai.",
        "extractedFields": [
            "Gate Pass No: 1000366218",
            "Date: 21-07-2026 2:59:48 PM",
            "Vehicle No: UP13ET6284",
            "Remarks: SURAJPUR MAKHAINA BLOCK DIBAI",
            "ANTI THEPT NUT BOLT: 120.000 EA",
            "RAFTER 3350: 5.000 EA",
            "PURLIN (3697, 2950, 5100): 18.000 EA",
            "BRACING (1467, 1424, 3180): 14.000 EA",
            "CONNECTION PLATE: 20.000 EA",
            "EARTHING ROD & LA: 2.000 EA",
            "DCDB BOX 1IN: 4.000 EA",
            "STRIP PATTI: 50.000 EA",
            "Total Qty: 238.000",
            "Outward No: 6311, Date: 21-07-2026",
            "Sign: Lalit"
        ]
    },
    {
        "id": "apex_doc_24_gatepass_1000366566",
        "file": "media_1795368685639_9e8fada6.jpg",
        "title": "Gate Pass 1000366566 - Inverter & Batteries (Surajpur)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000366566",
        "documentDateIso": "2026-07-22",
        "filedDateIso": "2026-07-22",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR WORK 14295",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for Inverter, 2 Batteries with serial numbers for Surajpur Makhaina Block Dibai.",
        "extractedFields": [
            "Gate Pass No: 1000366566",
            "Date: 22-07-2026 10:59:24 AM",
            "Vehicle No: UP16LT1748",
            "Remarks: SURAJPUR MAKHAINA BLOCK DIBAI",
            "Battery Serials: TT1700SS0206262329, TT1700SS0206262296",
            "Inverter Serial: SU2675X2005260074",
            "INVERTER 2300W 24VDC 2KVA: 1.000 EA",
            "BATTERY 12V 150AH: 2.000 NOS",
            "CABLE TIE: 4.000 P",
            "Outward No: 6317, Date: 22-07-2026",
            "Sign: Lalit"
        ]
    },
    {
        "id": "apex_doc_25_gatepass_1000347578",
        "file": "media_1795368685663_5d423281.jpg",
        "title": "Gate Pass 1000347578 - 400 M10 Nut Bolts (By Hand)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000347578",
        "documentDateIso": "2026-06-03",
        "filedDateIso": "2026-06-03",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 400 EA M10 Nut Bolts issued by hand for MRIS No-14018 Siyana Block.",
        "extractedFields": [
            "Gate Pass No: 1000347578",
            "Date: 03-06-2026 10:12:55 AM",
            "Vehicle No: BY HAND",
            "Remarks: MRIS NO-14018 SIYANA BLOCK",
            "M10 NUT BOLT: 400.000 EA",
            "Outward No: 6005, Date: 03/06/2026",
            "Sign: Nishant"
        ]
    },
    {
        "id": "apex_doc_26_gatepass_1000349864",
        "file": "media_1795368685687_aa9a7d83.jpg",
        "title": "Gate Pass 1000349864 - 150 M10 Bolt & Nut (Karimnagar)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000349864",
        "documentDateIso": "2026-06-09",
        "filedDateIso": "2026-06-09",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR (5369692)",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 150 M10 Bolt and Nut for Karimnagar Banboi.",
        "extractedFields": [
            "Gate Pass No: 1000349864",
            "Date: 09-06-2026 9:54:47 AM",
            "Vehicle No: BY HAND",
            "Remarks: MRIS NO-14025 BLOCK BB NAGAR BANBOI",
            "M10 BOLT AND NUT: 150.000 EA",
            "Outward No: 6052, Date: 09-06-2026",
            "Sign: Lalit"
        ]
    },
    {
        "id": "apex_doc_27_gatepass_1000345822",
        "file": "media_1795368685716_205c9e17.jpg",
        "title": "Gate Pass 1000345822 - DCDB 4in1 & Cable Ties (Kharkhali)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000345822",
        "documentDateIso": "2026-05-28",
        "filedDateIso": "2026-05-28",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR 14008",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for cable ties and DCDB 4 in 1 out at Kharkhali.",
        "extractedFields": [
            "Gate Pass No: 1000345822",
            "Date: 28-05-2026 10:34:25 AM",
            "Vehicle No: BY HAND",
            "Remarks: KHARKHALI BLOCK SIYANA",
            "cable tie: 3.000 EA",
            "dcdb 4 in 1 out: 1.000 EA",
            "Outward No: 5971, Date: 28/05/2026",
            "Sign: Nishant"
        ]
    },
    {
        "id": "apex_doc_28_gatepass_1000339678",
        "file": "media_1795368685749_48ef5d94.jpg",
        "title": "Gate Pass 1000339678 - Structure & Hardware (Khartali)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000339678",
        "documentDateIso": "2026-05-11",
        "filedDateIso": "2026-05-11",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR EMRIS NO. 13914",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 577 total structure pieces (rafters, purlins, bracings, hardware, cube mould).",
        "extractedFields": [
            "Gate Pass No: 1000339678",
            "Date: 11-05-2026 3:44:27 PM",
            "Vehicle No: UP14LT5707",
            "Remarks: KHARTALI BLOCK B.B.NAGAR",
            "Rafter (3342, 1400): 11 EA",
            "Purlin (5100, 3697, 2950): 34 EA",
            "Bracing (1467, 1424, 3180, 1104): 27 EA",
            "Connection plate: 83 EA",
            "Nut bolt (m10): 410 EA",
            "Cube mould: 6 EA",
            "Total Qty: 577.000",
            "Outward No: 5827, Date: 11/05/2026"
        ]
    },
    {
        "id": "apex_doc_29_gatepass_1000337634",
        "file": "media_1795368685779_86fd0a38.jpg",
        "title": "Gate Pass 1000337634 - Flexible Pipe (Hajipur)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000337634",
        "documentDateIso": "2026-05-06",
        "filedDateIso": "2026-05-06",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR 13906",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for flexible pipe issued by hand for Hajipur.",
        "extractedFields": [
            "Gate Pass No: 1000337634",
            "Date: 06-05-2026 10:04:22 AM",
            "Vehicle No: BY HAND",
            "Remarks: HAJIPUR BLOCK SIYANA",
            "flexible pipe: 1.000 EA",
            "Outward No: 5783, Date: 06/05/2026",
            "Sign: Akash Kumar"
        ]
    },
    {
        "id": "apex_doc_30_gatepass_1000346770",
        "file": "media_1795368685812_62b50e15.jpg",
        "title": "Gate Pass 1000346770 - Structure & Cement 15 Bags (Karimnagar)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000346770",
        "documentDateIso": "2026-05-30",
        "filedDateIso": "2026-05-30",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR MEP 14015",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for Columns, Rafters, Purlins, Clits, Cement for Karimnagar Banboi.",
        "extractedFields": [
            "Gate Pass No: 1000346770",
            "Date: 30-05-2026 4:25:34 PM",
            "Vehicle No: UP13BT3326",
            "Remarks: KARIMNAGAR BANBOI BLOCK BB NAGAR",
            "COLUMN: 17.000 EA",
            "RAFTER: 17.000 EA",
            "PURLING: 42.000 EA",
            "CLIT: 62.000 EA",
            "OPC CEMENT: 15.000 BAG",
            "CONCTION PLATE: 34.000 EA",
            "BRACING: 37.000 EA",
            "Total Qty: 224.000",
            "Outward No: 5981, Date: 30/05/2026",
            "Sign: Nishant"
        ]
    },
    {
        "id": "apex_doc_31_gatepass_1000345122",
        "file": "media_1795368685842_faeb5cfe.jpg",
        "title": "Gate Pass 1000345122 - 17 PV Modules & VFD 20HP (Kharkhali)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000345122",
        "documentDateIso": "2026-05-26",
        "filedDateIso": "2026-05-26",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR 14002",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 17 PV Modules, VFD Panel, DCDBs, Earthing Rods, Chemical Bags at Kharkhali.",
        "extractedFields": [
            "Gate Pass No: 1000345122",
            "Date: 26-05-2026 12:03:02 PM",
            "Vehicle No: UP13ET6284",
            "Remarks: KHARKHALI BLOCK BB NAGAR",
            "PV MODULES 400W: 17.000 EA",
            "VFD PANEL 20 HP: 1.000 EA",
            "DCDB 1IN 1 OUT: 1.000 EA",
            "DCDB 3IN: 1.000 EA",
            "ANTI THEFT BOLT: 100.000 EA",
            "SPIKELA: 1.000 EA",
            "FLEXIBLE PIPE: 3.000 BAG",
            "EARTHING TRIP: 90.000 M",
            "EARTHING ROD: 5.000 EA",
            "CHEMICAL BAG: 5.000 EA",
            "Total Qty: 224.000",
            "Outward No: 5946, Date: 26/05/2026",
            "Sign: Nishant"
        ]
    },
    {
        "id": "apex_doc_32_gatepass_1000332816",
        "file": "media_1795368685871_8e4b1c2c.jpg",
        "title": "Gate Pass 1000332816 - 565 Nut Bolts (Hajipur)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000332816",
        "documentDateIso": "2026-04-21",
        "filedDateIso": "2026-04-21",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR WORK 13710",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 440 Nut Bolt 8mm and 125 Nut Bolt 10mm for Hajipur.",
        "extractedFields": [
            "Gate Pass No: 1000332816",
            "Date: 21-04-2026 1:38:29 PM",
            "Vehicle No: BY HAND",
            "Remarks: HAJIPUR BLOCK SYN",
            "NUT BOLT 8MM: 440.000 EA",
            "NUT BOLT 10MM: 125.000 EA",
            "Total Qty: 565.000",
            "Outward No: 5693, Date: 21/04/2026",
            "Sign: Akash Kumar"
        ]
    },
    {
        "id": "apex_doc_33_gatepass_1000333958",
        "file": "media_1795368685898_dce462d9.jpg",
        "title": "Gate Pass 1000333958 - Plates & Column (E-Rikshaw)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000333958",
        "documentDateIso": "2026-04-24",
        "filedDateIso": "2026-04-24",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR WORK",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for Connection Plates, Clits, and Column transported by E-Rikshaw for Siyana.",
        "extractedFields": [
            "Gate Pass No: 1000333958",
            "Date: 24-04-2026 11:12:23 AM",
            "Vehicle No: E RIKSHAW",
            "Remarks: MRIS NO-13714 BLOCK SIYANA",
            "CANECTION PLATE: 6.000 EA",
            "CLIT (1X5 TABLE): 6.000 EA",
            "COLUMAN 3350 MM: 1.000 EA",
            "Total Qty: 13.000",
            "Outward No: 5718, Date: 24/04/2026",
            "Sign: Nishant"
        ]
    },
    {
        "id": "apex_doc_34_gatepass_1000332000",
        "file": "media_1795368685932_02365ef7.jpg",
        "title": "Gate Pass 1000332000 - Full Structure & Cement 20 Bags (Hajipur)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000332000",
        "documentDateIso": "2026-04-18",
        "filedDateIso": "2026-04-18",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR WORKS - 13703",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 196 total items: Columns, Rafters, Purlins, Bracings, Plates, Clits, Cement for Hajipur.",
        "extractedFields": [
            "Gate Pass No: 1000332000",
            "Date: 18-04-2026 5:07:28 PM",
            "Vehicle No: UP13CT5535",
            "Remarks: HAJIPUR - SYANA",
            "column (3350mm): 16 EA",
            "rafter (3342, 1400): 17 EA",
            "purlin (3697, 2950, 4104, 5125): 22 EA",
            "bracing (1467, 1424, 3180, 3086, 1108): 37 EA",
            "connection plate & clit: 84 EA",
            "cement: 20 BAG",
            "Total Qty: 196.000",
            "Outward No: 5682, Date: 18/04/2026",
            "Sign: Akash Kumar"
        ]
    },
    {
        "id": "apex_doc_35_gatepass_1000334984",
        "file": "media_1795368685960_0ac0d6bf.jpg",
        "title": "Gate Pass 1000334984 - 300 Anti Theft Nut Bolts (Siyana)",
        "category": "DELIVERY_CHALLAN",
        "referenceNumber": "1000334984",
        "documentDateIso": "2026-04-27",
        "filedDateIso": "2026-04-27",
        "organization": "KALPATARU PROJECTS INTERNATIONAL LTD.",
        "contractorOrParty": "APEX SOLAR",
        "department": "Jal Jeevan Mission",
        "summary": "Gate pass for 300 EA Anti Theft Nut Bolts for MRIS No-13726 Siyana Block.",
        "extractedFields": [
            "Gate Pass No: 1000334984",
            "Date: 27-04-2026 2:26:32 PM",
            "Vehicle No: BY HAND",
            "Remarks: MRIS NO-13726 SIYANA BLOCK",
            "ANTI THEFT NUT BOLT: 300.000 EA",
            "Total Qty: 300.000",
            "Outward No: 5739, Date: 27-04-2026",
            "Sign: Lalit"
        ]
    }
]

def main():
    print(f"Total documents to process: {len(DOCUMENTS)}")
    success_count = 0
    results = []

    for idx, doc in enumerate(DOCUMENTS, 1):
        doc_id = doc["id"]
        img_name = doc["file"]
        img_path = os.path.join(IMG_DIR, img_name)

        if not os.path.exists(img_path):
            print(f"[{idx}/{len(DOCUMENTS)}] ERR: File not found: {img_path}")
            continue

        url = RELAY_URL_TEMPLATE.format(doc_id=doc_id)
        try:
            with open(img_path, "rb") as f:
                files = {"file": (f"{doc_id}.jpg", f, "image/jpeg")}
                data = {"file_type": "photo"}
                res = requests.post(url, data=data, files=files, verify=False, timeout=60)
                if res.status_code == 200:
                    r_json = res.json()
                    tg_msg_id = r_json.get("telegram_message_id")
                    print(f"[{idx}/{len(DOCUMENTS)}] OK {doc_id} -> Telegram message ID: {tg_msg_id}")
                    doc["telegramMessageId"] = tg_msg_id
                    success_count += 1
                else:
                    print(f"[{idx}/{len(DOCUMENTS)}] FAIL {doc_id}: {res.status_code} - {res.text}")
        except Exception as e:
            print(f"[{idx}/{len(DOCUMENTS)}] EXCEPTION {doc_id}: {e}")

        results.append(doc)
        time.sleep(1.0) # avoid telegram flood rate-limits

    output_json = "/data/user/0/com.termux/files/home/projects/Apex-Solar-Work/all_35_documents.json"
    with open(output_json, "w") as f:
        json.dump(results, f, indent=2)

    print(f"\nDone! Successfully uploaded {success_count}/{len(DOCUMENTS)} to Telegram Channel.")
    print(f"Master metadata saved to: {output_json}")

if __name__ == "__main__":
    main()
