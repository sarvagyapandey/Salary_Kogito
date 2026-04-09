#!/bin/bash

# Test if Performance Bonus component is generated for an employee with CTC >= 300000
QUERY='query {
  calculateSalary(
    employeeId: "test_emp"
    name: "Test Employee"
    ctc: 600000
    cca: 10000
    category: "Staff"
    location: "HQ"
  ) {
    employeeId
    name
    ctc
    basic
    hra
    componentList {
      name
      amount
      type
    }
  }
}'

echo "Testing Performance Bonus for CTC 600000..."
timeout 10 curl -s "http://localhost:8080/graphql" \
  -H "Content-Type: application/json" \
  -d "{\"query\": \"$(echo $QUERY | sed 's/"/\\"/g')\"}" | jq . 2>/dev/null

if [ $? -ne 0 ]; then
  echo "Error or timeout. Backend may not be responsive."
fi
