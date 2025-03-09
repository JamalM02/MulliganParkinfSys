# Acceptance Testing Plan for Mulligan Parking System

## System Use Case 1: Starting a Parking Event

| #   | Artifact Tested | Pre-conditions                        | Test Steps                                   | Expected Result                          | Passed? |
|-----|-----------------|---------------------------------------|----------------------------------------------|------------------------------------------|---------|
| 1   | Customer App    | Customer logged in                   | (a) Select "Start Parking"                   | Screen to enter parking shown            | Pass    |
|     |                 |                                       | (b) Enter valid parking space number         | Parking started message shown            |         |
| 2A  | Customer App    | Customer logged in                   | (a) Select "Start Parking"                   | Invalid space message shown              | Pass    |
|     |                 |                                       | (b) Enter invalid parking space number       | Repeat step for valid space              |         |
| 3B  | Customer App    | Customer logged in, another parking event exists | (a) Select "Start Parking"        | Stop parking for existing event and start new one | Pass    |

---

## System Use Case 2: Stopping a Parking Event

| #   | Artifact Tested | Pre-conditions                        | Test Steps                                   | Expected Result                          | Passed? |
|-----|-----------------|---------------------------------------|----------------------------------------------|------------------------------------------|---------|
| 1   | Customer App    | Customer logged in                   | (a) Select "Stop Parking"                    | Parking Stopped message shown            | Pass    |
| 2A  | Customer App    | Customer logged in                   | (a) Select "Stop Parking" for non-existing   | Error message "No parking to stop"       | Pass    |

---

## System Use Case 3: Retrieving List of Parking Events

| #   | Artifact Tested | Pre-conditions                        | Test Steps                                   | Expected Result                          | Passed? |
|-----|-----------------|---------------------------------------|----------------------------------------------|------------------------------------------|---------|
| 1   | Customer App    | Customer logged in                   | (a) Select "Get Parking Events List"         | List of parking events displayed         | Pass    |
| 2A  | Customer App    | Customer logged in                   | (a) Select "Get Parking Events List"         | Message "No events available"            | Pass    |

---

## System Use Case 4: Investigating Parked Vehicle

| #   | Artifact Tested | Pre-conditions                        | Test Steps                                   | Expected Result                          | Passed? |
|-----|-----------------|---------------------------------------|----------------------------------------------|------------------------------------------|---------|
| 1   | PEO App         | PEO logged in                        | (a) Select "Check Vehicle"                   | Vehicle status as parked displayed        | Pass    |
| 2A  | PEO App         | PEO logged in                        | (a) Enter invalid vehicle or space number    | Error message displayed                  | Pass    |
| 3B  | PEO App         | PEO logged in, no ongoing parking    | (a) Select "Check Vehicle"                   | Parking "Not OK", initiate citation       | Pass    |

---

## System Use Case 5: Citation Issuance

| #   | Artifact Tested | Pre-conditions                        | Test Steps                                   | Expected Result                          | Passed? |
|-----|-----------------|---------------------------------------|----------------------------------------------|------------------------------------------|---------|
| 1   | PEO App         | PEO logged in                        | (a) Select "Issue Citation"                  | Citation issued and saved                | Pass    |
| 2A  | PEO App         | PEO logged in                        | (a) Enter invalid vehicle or parking ID      | Error message displayed                  | Pass    |

---

## System Use Case 6: Parking Transaction Report

| #   | Artifact Tested | Pre-conditions                        | Test Steps                                   | Expected Result                          | Passed? |
|-----|-----------------|---------------------------------------|----------------------------------------------|------------------------------------------|---------|
| 1   | MO App          | MO logged in                         | (a) Select "Get Transaction Report"          | List of transactions displayed           | Pass    |
| 2A  | MO App          | MO logged in                         | (a) Select "Get Transaction Report"          | Message "No transactions available"      | Pass    |

---

## System Use Case 7: Parking Citation Report

| #   | Artifact Tested | Pre-conditions                        | Test Steps                                   | Expected Result                          | Passed? |
|-----|-----------------|---------------------------------------|----------------------------------------------|------------------------------------------|---------|
| 1   | MO App          | MO logged in                         | (a) Select "Get Citation Report"             | List of citations displayed              | Pass    |
| 2A  | MO App          | MO logged in                         | (a) Select "Get Citation Report"             | Message "No citations available"         | Pass    |

## System Use Case 8: RabbitMQ Node Failure and Recovery

| #   | Artifact Tested | Pre-conditions                       | Test Steps                             | Expected Result                    | Passed? |
|-----|-----------------|--------------------------------------|----------------------------------------|------------------------------------|---------|
| 1   | RabbitMQ Consumer| RabbitMQ node is running	            | (a) Simulate RabbitMQ node failure     | Consumer retries connection	       | Pass    |
|     |                 |                                      | (b) Restore RabbitMQ node              | Consumer reconnects and resumes operations|         |
| 2A  |RabbitMQ Consumer| RabbitMQ node is running, handling messages| (a) Simulate RabbitMQ node failure while processing a message| Consumer retries processing message| Pass    |

## System Use Case 9: Timeout in RabbitMQ Operations

| #   | Artifact Tested | Pre-conditions                     | Test Steps                                | Expected Result                         | Passed? |
|-----|----------------|------------------------------------|-------------------------------------------|-----------------------------------------|---------|
| 1   | RabbitMQ Sender| RabbitMQ running	                  | (a) Send a transaction message	           | Timeout exception is handled gracefully | Pass    |
| 2A  | RabbitMQ Consumer | RabbitMQ running	                  | (a) Send a message with delayed processing| Timeout exception does not crash the consumer| Pass    |

## System Use Case 10: Handling Invalid Data in RabbitMQ

| #   | Artifact Tested | Pre-conditions                     | Test Steps                   | Expected Result                         | Passed? |
|-----|----------------|------------------------------------|------------------------------|-----------------------------------------|---------|
| 1   | RabbitMQ Consumer| RabbitMQ running	                  | (a) Send malformed message| Consumer logs error and ignores the message	| Pass    |

## System Use Case 11: Handling Concurrent Requests

| #   | Artifact Tested | Pre-conditions                   | Test Steps                                | Expected Result                         | Passed? |
|-----|----------------|----------------------------------|-------------------------------------------|-----------------------------------------|---------|
| 1   | RabbitMQ Sender| Multiple threads active		        | (a) Simultaneously send parking events from multiple users| All requests are processed without error	| Pass    |
| 2A  | RabbitMQ Consumer | RabbitMQ running	                | (a) Simultaneously process messages from multiple queues| No deadlocks or race conditions	| Pass    |

## System Use Case 12: Null or Malformed Inputs

| #   | Artifact Tested | Pre-conditions                   | Test Steps                                | Expected Result                         | Passed? |
|-----|----------------|----------------------------------|-------------------------------------------|-----------------------------------------|---------|
| 1   | RabbitMQ Sender| RabbitMQ running			        | (a) Send null as input	| NullPointerException handled gracefully	| Pass    |
| 2A  | RabbitMQ Consumer | RabbitMQ running	                | (a) Process message with unexpected data format| Consumer logs error and skips processing| Pass    |

## System Use Case 13: Database Operations

| #   | Artifact Tested | Pre-conditions         | Test Steps                              | Expected Result                         | Passed? |
|-----|----------------|------------------------|-----------------------------------------|-----------------------------------------|---------|
| 1   | MongoDB	| MongoDB running	 | (a) Insert parking transaction| Data is correctly saved to the database	| Pass    |
| 2A  | MongoDB | MongoDB running	      | (a) Query transactions with invalid parameters| Database returns no results or logs error| Pass    |

## System Use Case 14: High-Load Operations

| #   | Artifact Tested | Pre-conditions         | Test Steps                              | Expected Result                       | Passed? |
|-----|---------------|------------------------|-----------------------------------------|---------------------------------------|---------|
| 1   | Entire System	| Multiple users logged in| (a) Simulate high traffic with concurrent parking requests|System remains responsive, no crashes| Pass    |
| 2A  | Entire System | Multiple users logged in | (a) Retrieve large parking or citation reports| System handles requests within acceptable time| Pass    |

## System Use Case 15: Server Start and Stop

| #   | Artifact Tested | Pre-conditions         | Test Steps                              | Expected Result                       | Passed? |
|-----|---------------|------------------------|-----------------------------------------|---------------------------------------|---------|
| 1   | ServerApp		| Server is stopped	| (a) Start the server	|Server starts without errors	| Pass    |
| 2A  | ServerApp	 | Server is running	|(a) Stop the server	| Server shuts down gracefully	| Pass    |



## System Use Case 16: Parking Space Recommendation

| #   | Artifact Tested   | Pre-conditions                              | Test Steps                                         | Expected Result                                      | Passed? |
|-----|-------------------|--------------------------------------------|----------------------------------------------------|------------------------------------------------------|---------|
| 1   | Mulligan System  | Parking Customer logged in                 | (a) Select "Recommend Parking"                    | Screen to enter parking space number is shown       | Pass    |
| 2   | Mulligan System  | Parking Customer selected "Recommend Parking" | (a) Enter a parking space number                 | System validates parking space number               | Pass    |
| 3A  | Mulligan System  | Valid parking space number entered          | (a) Find nearest available parking space(s) with minimum citations | Display list of best parking spaces                | Pass    |
| 3B  | Mulligan System  | Multiple available spaces (excluding entered space) | (a) Return closest available space(s) with min citations | Display recommended parking spaces                  | Pass    |
| 3C  | Mulligan System  | Multiple available spaces (including entered space) | (a) Return entered parking space                 | Display selected parking space                      | Pass    |
| 3D  | Mulligan System  | No available parking spaces                 | (a) Return empty list                            | Display message "No parking spaces available"      | Pass    |
| 3E  | Mulligan System  | Invalid parking space number entered        | (a) Show error message                          | Display error message and return to step 2         | Pass    |


## System Use Case 17: Recommender System Failure Handling

| #   | Artifact Tested   | Pre-conditions                              | Test Steps                                         | Expected Result                                      | Passed? |
|-----|-------------------|--------------------------------------------|----------------------------------------------------|------------------------------------------------------|---------|
| 1   | Recommender System | At least 1 recommender server is down     | (a) Request parking recommendation               | System continues functioning with remaining servers | Pass    |
| 2   | Recommender System | 2 recommender servers are down             | (a) Request parking recommendation               | System degrades gracefully, using cached data or fallback | Pass    |
| 3   | Recommender System | 1 recommender server returns incorrect data | (a) Request parking recommendation               | System detects inconsistency and excludes faulty server | Pass    |
| 4   | Recommender System | 2 recommender servers return incorrect data | (a) Request parking recommendation               | System flags the issue and prompts user with warning | Pass    |


