# System design

[← Back to the README](../README.md#system-design-diagrams)

Detailed data-flow and actor/use-case views for the Disaster Relief Resource Allocator.

## Data flow diagrams

These are **logical DFDs**, using Mermaid for GitHub-native rendering. Rectangles represent external entities, rounded nodes represent processes, cylinders represent logical data stores, and labelled arrows represent data. Store groups are tables within the same PostgreSQL database, not separate databases. Arrows describe information movement, not the movement of physical goods.

### Level 0 — system context

The entire application is one process. Relief staff includes coordinators, inventory managers, field agents, partner managers and viewers; each action is subject to that user's permissions.

```mermaid
flowchart TD
    Admin["System administrator"]
    Staff["Operational user including administrator"]
    IdP["External identity provider"]
    System("0. Disaster Relief Resource Allocator")

    Admin -->|Organization and user changes; audit queries| System
    System -->|Administration results and audit history| Admin
    Staff -->|Disaster, stock, request, allocation and shipment data; queries| System
    System -->|Operational records, progress and validation results| Staff
    System -->|Sign-in protocol requests; metadata and key requests| IdP
    IdP -->|Identity tokens, metadata and public signing keys| System
```

### Level 1 — major application processes

**Level 1A — access, preparation and demand.**

The context process decomposes into six responsibilities, shown in two connected views. P2 groups disaster/location/catalogue and inventory operations; P6 groups read views and audit capture. Shared authorization applies before operational processes. Administrator operations use the same business processes with cross-organization scope where permitted.

```mermaid
flowchart TD
    Admin["System administrator"]
    Staff["Operational user including administrator"]
    IdP["External identity provider"]
    P1("1.0 Authenticate and administer")
    P2("2.0 Manage relief context and stock")
    P3("3.0 Manage resource requests")
    P6("6.0 Audit capture")
    D1[("D1 Organizations and profiles")]
    D2[("D2 Context and stock")]
    D3[("D3 Requests and items")]

    Admin -->|Profile and organization changes| P1
    P1 -->|Administration results| Admin
    Staff <-->|Sign-in and profile data| P1
    P1 <-->|OIDC, metadata and keys| IdP
    P1 <-->|Profiles and local roles| D1
    Staff <-->|Context and stock changes/results| P2
    P2 <-->|Context and quantities| D2
    Staff <-->|Drafts, reviews and status| P3
    D1 -->|Requester and organization| P3
    D2 -->|Destination and resources| P3
    P3 <-->|Requests and items| D3
    P1 -->|Administration events| P6
    P2 -->|Context and stock events| P6
    P3 -->|Request events| P6
```

**Level 1B — allocation, shipment and visibility.** P6 and D1–D3 are the same logical processes/stores referenced above; they are repeated for readability.

```mermaid
flowchart TD
    Staff["Operational user including administrator"]
    Admin["System administrator"]
    P4("4.0 Allocate resources")
    P5("5.0 Manage shipments")
    P6("6.0 Record audit and serve read views")
    D1[("D1 Organizations and profiles")]
    D2[("D2 Context and stock")]
    D3[("D3 Requests and items")]
    D4[("D4 Allocations and shipments")]
    D5[("D5 Audit events")]

    Staff <-->|Instructions, history and results| P4
    D2 <-->|Stock and reservations| P4
    D3 <-->|Demand and progress| P4
    P4 <-->|Allocation records| D4
    P4 -->|Reservation events| P6
    Staff <-->|Shipment actions and results| P5
    D2 <-->|Destination and balances| P5
    D3 <-->|Delivery progress| P5
    D4 <-->|Manifests and states| P5
    P5 -->|Shipment events| P6
    Staff <-->|Queries and scoped read views| P6
    Admin <-->|Audit filters and history| P6
    D1 -->|Scoped profile data| P6
    D2 -->|Scoped context data| P6
    D3 -->|Scoped request data| P6
    D4 -->|Scoped fulfillment data| P6
    P6 -->|Successful change events| D5
    D5 -->|Audit records| P6
```

Administrators also participate through the operational-user entity. P6's audit writes occur **inside the originating business transaction**, not through an asynchronous event pipeline. An audit append failure rolls back the business change.

### Level 2 — decomposition of 4.0 Allocate resources

This view expands P4 while retaining its Level 1 boundaries: allocation instructions/results, stock, requests, allocations and audit events. Access checks happen at the HTTP boundary; services lock the request and inventory before changing quantities.

```mermaid
flowchart TD
    Actor["Authorized operational user"]
    P41("4.1 Load and validate request")
    P42("4.2 Lock matching stock")
    P43("4.3 Calculate reservation quantities")
    P44("4.4 Persist reservations and request progress")
    P45("4.5 Read allocation history")
    D2[("D2 Context and stock")]
    D3[("D3 Requests and items")]
    D4[("D4 Allocations and shipments")]
    P6("6.0 Record audit and serve read views")

    Actor -->|Request ID or exact reservation data| P41
    D3 -->|Locked request data| P41
    P41 -->|Validated demand| P42
    D2 -->|Locked available stock| P42
    P42 -->|Stock and demand| P43
    P43 -->|Valid quantities| P44
    P44 -->|Updated reserved quantities| D2
    P44 -->|Allocation progress| D3
    P44 -->|New RESERVED allocations| D4
    P44 -->|Audit events| P6
    P44 -->|Allocation result| Actor
    P41 -->|Invalid request result| Actor
    P42 -->|Invalid stock selection result| Actor
    P43 -->|Invalid quantity result| Actor

    Actor -->|Request item history query| P45
    D3 -->|Existing request item| P45
    D4 -->|Prior allocations for the item| P45
    P45 -->|Allocation history or missing-item error| Actor
```

Automatic allocation reserves **min(remaining need, available stock)** from matching inventory in the destination's organization, ordered by inventory UUID. It can leave demand partially allocated. Exact reservations reject nonpositive quantities, mismatched resources, cross-organization stock, excess demand or insufficient availability. This is stock-matching allocation; the implementation does not optimize geographic routes or globally rank competing requests.

## Use case diagrams

These diagrams use **UML-style actor/use-case associations expressed as Mermaid flowcharts**: external rectangles are actors, oval nodes are use cases, and subgraphs define the application boundary. Associations show participation, not execution order. The views are split for readability.

### Administration and access

```mermaid
flowchart LR
    Admin["System administrator"]
    User["Any provisioned active user"]
    IdP["External identity provider"]

    subgraph System["Application use cases"]
        Login(["Sign in through OIDC"])
        Profile(["View own profile and organization"])
        Orgs(["Manage organizations"])
        Users(["Provision users and manage local roles"])
        Audit(["Search and inspect audit history"])
    end

    User --- Login
    IdP --- Login
    User --- Profile
    Admin --- Login
    Admin --- Profile
    Admin --- Orgs
    Admin --- Users
    Admin --- Audit
```

The application binds provider identities to local profiles; it does not issue passwords or provide public self-registration. Global administrators have no organization membership.

### Disaster, inventory and request operations

```mermaid
flowchart LR
    Admin["System administrator"]
    Coordinator["Disaster coordinator"]
    Inventory["Inventory manager"]
    Field["Field agent"]
    Partner["Partner manager"]

    subgraph System["Application use cases"]
        Disaster(["Manage disasters and affected locations"])
        Stock(["Manage catalogue and inventory"])
        Location(["Create locations"])
        Request(["Create, edit and submit requests"])
        Review(["Approve or reject requests"])
    end

    Admin --- Disaster
    Admin --- Stock
    Admin --- Location
    Admin --- Request
    Admin --- Review
    Coordinator --- Disaster
    Coordinator --- Review
    Inventory --- Stock
    Partner --- Location
    Partner --- Request
    Field --- Request
```

### Allocation, shipment and operational visibility

```mermaid
flowchart LR
    Admin["System administrator"]
    Coordinator["Disaster coordinator"]
    Inventory["Inventory manager"]
    Field["Field agent"]
    ReadOnly["Partner manager or viewer"]

    subgraph System["Application use cases"]
        Allocate(["Automatically allocate or reserve exact stock"])
        Shipment(["Create, dispatch or cancel pending shipment"])
        Deliver(["Confirm shipment delivery"])
        Read(["View permitted operational records and allocation history"])
    end

    Admin --- Allocate
    Admin --- Shipment
    Admin --- Deliver
    Admin --- Read
    Coordinator --- Allocate
    Coordinator --- Shipment
    Coordinator --- Deliver
    Coordinator --- Read
    Inventory --- Allocate
    Inventory --- Shipment
    Inventory --- Deliver
    Inventory --- Read
    Field --- Deliver
    Field --- Read
    ReadOnly --- Read
```

Operational reads remain subject to endpoint-specific permissions and organization scope. For example, inventory/catalogue APIs are restricted to administrators and inventory managers; membership does not grant unrestricted access to every dataset. The API permits coordinators and inventory managers to reserve exact quantities, although the current frontend exposes exact reservation to inventory managers.

