# 323CA CONSTANTINESCU RARES

# Design patterns used in the project
Singleton
I utilized the Singleton pattern for the Database and NotificationService classes. This approach
ensures that there is exactly one instance of these components throughout the application's
life, which is essential for maintaining consistent data and centralized communication.
By implementing this pattern, I can access the database or notification system from any command
class without needing to pass these dependencies as parameters through every method call.

Command Pattern
The Command pattern serves as the backbone of the application's control flow. Since the input
is provided as a list of commands in a JSON file, I needed a structured way to process them.
I defined a Command interface, which allows every specific action such as AssignTicket or
AddComment to be encapsulated in its own class. This decoupling means the main application
logic remains clean and focuses only on calling the correct command from a registry. It
also allows for new commands to be added in the future without modifying the core execution loop.

Builder Pattern
To handle the complexity of creating ticket objects, I implemented the Builder pattern. The
system handles different types of tickets, each requiringa specific set of data in
addition to the standard fields like title and priority. Using a standard constructor
with a long list of parameters would be difficult to read and most likely lead to errors.
The Builder pattern resolves this by allowing me to construct a ticket step-by-step,
setting only the necessary fields in a clear and readable manner.

Observer Pattern
I implemented the Observer pattern to manage the notification system. The goal was to make sure
that users are automatically notified when relevant events occur, such as when a ticket status
changes. In this design, the NotificationService acts as the subject that distributes updates,
while the User class acts as the observer that receives them. This separates the logic for
sending notifications from the user implementation, allowing the system to easily broadcast messages.

# The structure of the project
# main
This is the root package where the application starts.
App.java: The entry point of the program. It reads the input JSON file, matches the command strings
to the right classes, and runs them.
Database.java: This acts as the memory of the application. It holds lists of all users, tickets, and
milestones. Since it’s a Singleton, the data stays consistent while the program runs.
Milestone.java: Represents a project milestone. It tracks deadlines, assigned tickets, and
which developers are working on it.

# main.commands
This is where the actual logic happens. I used the Command pattern here, so every action
you can take in the app has its own class.
Command Interface: Defines the execute method that all commands must implement.
Concrete Commands: Classes like ReportTicketCommand, AssignTicketCommand, and AppStabilityReportCommand.
Each one handles a specific task, validation, and updates the database.

# main.users
This package defines the different types of people who use the system.
User.java: The abstract base class for everyone. It holds common info like username and email.
DeveloperUser, ManagerUser, ReporterUser: These extend the User class. Developers have specific
fields for expertise and seniority, while Managers have a list of subordinates.
SecurityManager.java: A helper class I made to check perissions. It verifies if a developer has the
right seniority and skills to take on a specific ticket.

# main.tickets
This package contains the data models for the work items.
Ticket.java: The abstract parent class containing shared fields like ID, title, status, and priority.
BugTicket, FeatureRequestTicket, UiFeedbackTicket: Specific implementations of a ticket. For example,
BugTicket has fields for "reproduction steps," while FeatureRequestTicket tracks "business value."

# main.notifications
This package handles the communication part of the system.
NotificationService.java: A Singleton that sends messages to users. It’s used whenever a status changes
or a milestone is due, ensuring users stay updated.
NotificationObserver: An interface that allows the User class to receive updates from the service.

# main.enums
This package just holds the constants used throughout the app.
It includes enums like Priority, Status, Seniority, and Expertise. I used these to avoid typing raw
strings everywhere.

# Implementation Logic

# Milestones and Priority Updates
In the Milestone class, I implemented a method called updateStatusAndPriorities that runs whenever we check the
system. This method checks the current date against the milestone deadlines. It automatically increases the
priority of active tickets every 3 days and handles the critical phase where tickets become CRITICAL
if the deadline is tomorrow or has passed. I also added logic here to check for dependencies, that makes sure
that a blocked milestone doesn't start or update its tickets until the blocking milestone is finished.

# Access Control
To apply the rules regarding who can work on what, I centralized the permission logic in the
SecurityManager class. Instead of using if statements throughout the commands, I put all the validation
in one place. The hasAccess method checks a developer's seniority against the ticket priority and validates
if their expertise matches the ticket type. This way, the AssignTicketCommand stays clean and I can
reuse this logic easily if I need to check permissions elsewhere.

# Reports and Metrics
A significant part of the application involves generating reports. I implemented the specific mathematical
formulas for these in their respective command classes. I used ChronoUnit to calculate the exact resolution
time in days. For the Stability and Risk reports, I simply applied the formulas to classify the application
state as Stable, Unstable, or Partially Stable based on the active bugs.

# Data Persistence and JSON
I used the Jackson library for all the input and output operations. The App class uses an ObjectMapper to
load the users and commands from the JSON files. One important detail is how I handled the different ticket
types. Since Ticket is an abstract class, I used Jackson annotations like @JsonTypeInfo and @JsonSubTypes
in the Ticket class. This allows the parser to automatically create the correct object type like a BugTicket
or FeatureRequestTicket based on the "type" field in the input file.
