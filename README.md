# 🚀 Mulligan Parking System - Distributed Systems Project

## 📌 Project Overview
The **Mulligan Parking System** is a distributed system designed for **efficient parking space management**. It enables customers to choose a parking zone, view available spaces, and select a slot dynamically. The system is built using **RabbitMQ** for messaging, **MongoDB** for data persistence, and follows best practices in **distributed computing**. The entire architecture is **clustered**, ensuring high availability and scalability.

## 👥 Contributors
| Name              | Role                          |
|---------------|-----------------------------|
| **Jamal Majadle**   | UI Development, Server, RabbitMQ, Recommender System, Docker |
| **Matan Shabi**   | DevOps, Database, Documentation, Docker |
| **Oran Alster**  | Server ,Testing, Javadoc |

---

## 📜 Documentation
- [📑 Testing Plan](TestAcceptance.md)
- [🗄️ Database Design](Database_Design_Document.md)
- [📬 Queue Management](Queue_Design_Document.md)
- [📖 Consensus Protocol](Consensus_Protocol.md)

---

## 🏗️ System Architecture
### 🔹 **Key Components**
- **Frontend**: Web-based UI for selecting parking zones and spaces
- **Backend**: Handles requests, updates availability, and processes reservations
- **Database**: Clustered MongoDB (3-node replica set for high availability) for storing parking space information
- **Messaging System**: Clustered RabbitMQ (3-node cluster for load balancing and fault tolerance) for handling distributed tasks
- **Recommender Service**: AI-powered Recommender System with 3 managed copies for redundancy for suggesting optimal parking spaces

### 🔹 **Workflow**
1. Customers **log in** and select a **parking zone**.
2. System displays **available spaces** in the selected zone.
3. Customers **choose a parking space**, making it **unavailable for others**.
4. **Vehicle registration** field is enabled only after selecting a parking space.
5. Confirmation is sent, and the **booking is processed asynchronously**.

---

## ⚙️ Technologies Used
| **Category** | **Technology** |
|-------------|---------------|
| **Programming Languages** | Java, JavaScript |
| **Frameworks** | Spring Boot, Express.js |
| **Database** | **Clustered MongoDB** |
| **Message Queue** | **Clustered RabbitMQ** |
| **Recommender System** | **Clustered AI-powered Recommender** |
| **Deployment & DevOps** | Docker, Kubernetes |
| **Testing** | JUnit, Mockito |

---

## 🛠️ Setup & Installation
### 1️⃣ **Clone the Repository**
```sh
git clone https://github.com/JamalM02/MulliganParkingSystem.git
cd MulliganParkingSystem
```

### 2️⃣ **Set Up Environment Variables**
Create a `.env` file in the root directory:
```sh
MONGO_URI=mongodb://cluster-url:27017/parking_system
RABBITMQ_URL=amqp://cluster-url
PORT=5000
```

### 3️⃣ **Run Docker Containers**
```sh
docker-compose up -d
```

### 4️⃣ **Start the Application**
```sh
./gradlew bootRun  # For backend
npm start          # For frontend (if applicable)
```

---

## 📌 Usage Instructions
- Access the **web UI** at `http://localhost:3000`
- Select a **parking zone**
- Choose an **available parking space**
- Enter **vehicle details** (enabled after space selection)
- Confirm **reservation** and receive status updates

---

## 🔍 Future Improvements
✅ Enhance **real-time parking availability updates**
✅ Implement **automated notifications for users**
✅ Introduce **dynamic pricing based on demand**

---

## 📫 Contact
📧 **Jamal Majadle** - [jamal.majadle02@gmail.com](mailto:jamal.majadle02@gmail.com)  
🔗 **LinkedIn** - [linkedin.com/in/jamal-majadle](https://www.linkedin.com/in/jamal-majadle)  
💻 **GitHub** - [github.com/JamalM02](https://github.com/JamalM02)  
