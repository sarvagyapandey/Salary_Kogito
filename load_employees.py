#!/usr/bin/env python3
"""
Load demo employee data into MongoDB
"""
import json
import sys
from pymongo import MongoClient

# Connect to MongoDB
client = MongoClient('mongodb://localhost:27018/')
db = client['payrollDB']
collection = db['employees']

# Load demo data
employees_data = [
    {
        "employeeId": "emp_16",
        "name": "Bruce",
        "ctc": 600000,
        "cca": 10000,
        "category": "Staff",
        "location": "Delhi",
        "pfOption": 3,
        "professionalTax": 200,
        "employeePFOverride": 1800
    },
    {
        "employeeId": "emp_17",
        "name": "Batman",
        "ctc": 42000,
        "cca": 0,
        "category": "DevOps",
        "location": "Delhi",
        "pfOption": 2,
        "professionalTax": 200,
        "employeePFOverride": None
    },
    {
        "employeeId": "emp_18",
        "name": "Rah",
        "ctc": 2000000,
        "cca": 1800,
        "category": "DevOps",
        "location": "Delhi",
        "pfOption": 5,
        "professionalTax": 200,
        "employeePFOverride": 5000
    }
]

try:
    # Clear existing data
    collection.delete_many({})
    print("✓ Cleared existing employee data")
    
    # Insert demo data
    result = collection.insert_many(employees_data)
    print(f"✓ Inserted {len(result.inserted_ids)} demo employees")
    
    # Verify
    count = collection.count_documents({})
    print(f"✓ Total employees in database: {count}")
    
    # Show inserted data
    print("\nInserted employees:")
    for emp in collection.find():
        print(f"  - {emp['employeeId']}: {emp['name']} (CTC: {emp['ctc']})")
    
    print("\n✓ Demo data loaded successfully!")
    
except Exception as e:
    print(f"❌ Error: {e}", file=sys.stderr)
    sys.exit(1)
finally:
    client.close()
