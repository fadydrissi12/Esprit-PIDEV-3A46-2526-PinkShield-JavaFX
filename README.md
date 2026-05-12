# PinkShield JavaFX Desktop Client

## Overview

**PinkShield** is the JavaFX Desktop Client of a hybrid medical ecosystem designed to connect patients and doctors through a shared digital healthcare platform.

This project was developed as part of the coursework for PIDEV 3A at Esprit School of Engineering (Academic Year 2025-2026).

The desktop application shares a centralized **MySQL database** with a Symfony web application, allowing both platforms to stay synchronized and provide a consistent experience across desktop and web environments.

## Features

- 🩺 **Medical Appointments**
  - Book appointments with doctors.
  - Track appointment status: pending, confirmed, postponed, or canceled.
  - Receive notifications for appointment updates.

- 🛒 **Online Parapharmacy**
  - Browse parapharmacy products.
  - Add items to wishlist/cart.
  - Complete transactions.
  - Generate and download beautiful **PDF receipts**.

- 📊 **Daily Health Tracking**
  - Track patient health-related information.
  - Support better follow-up and patient monitoring.

- 💬 **Interactive Medical Blog & Forum**
  - Create and view medical posts.
  - Comment, reply, like, edit, and delete posts.
  - Highlight doctor posts.
  - Use **AI bad words moderation** through the PurgoMalum API.
  - Send email notifications using the JavaMail API.

## Tech Stack

### Frontend

- **JavaFX**
- **FXML**
- **FontAwesomeFX**

### Backend

- **Java 17**
- **JDBC**
- **MySQL**
- Shared database: **pinkshield_db**

### Other Tools

- **API2PDF** for PDF receipts
- **PurgoMalum API** for profanity filtering
- **JavaMail API** for email notifications
- **Maven** for dependency management

## Directory Structure

```text
PinkShield-JavaFX/
├── src/
│   └── main/
│       ├── java/
│       │   └── tn/esprit/
│       │       ├── controllers/
│       │       ├── entities/
│       │       ├── services/
│       │       └── utils/
│       └── resources/
│           ├── fxml/
│           ├── images/
│           └── styles/
├── pom.xml
└── README.md
```

## Getting Started

1. **Clone the repository**

```bash
git clone https://github.com/fadydrissi12/Esprit-PIDEV-3A46-2526-PinkShield-JavaFX.git
cd Esprit-PIDEV-3A46-2526-PinkShield-JavaFX
```

2. **Set up the shared MySQL database**

Create or import the shared database used by both the JavaFX desktop client and the Symfony web application:

```sql
CREATE DATABASE pinkshield_db;
```

Then import the project SQL file if available.

3. **Configure the database connection**

Update the database configuration in the JavaFX project so it points to your local MySQL setup:

```text
Database: pinkshield_db
Host: localhost
User: your_mysql_user
Password: your_mysql_password
```

4. **Install Maven dependencies**

```bash
mvn clean install
```

5. **Run the application**

Open the project in your IDE, then run:

```text
MainFX.java
```

You can also run the project through Maven or your IDE JavaFX run configuration.

## Acknowledgments

This project was developed by the core team:

- **Fady Drissi**
- **Mehdi Tatar**
- **Nada Kadri**

Special thanks to **Esprit School of Engineering** for the academic framework and support during the PIDEV 3A project.

## License

This project is intended for academic and educational purposes as part of the PIDEV 3A coursework at Esprit School of Engineering.
