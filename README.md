# Meeting Planner Application

A service that enables users to manage their time slots, schedule meetings,
and view their calendar availability.

## Features

- **Time Slot Management** — create available time slots with configurable duration,
  delete or modify existing slots, and mark them as busy or free
- **Meeting Scheduling** — convert available slots into meetings with a title,
  description, and participants

## Design

### Time Slots

Time slots are modeled as a stream of fixed-duration "bricks", each containing:

| Field       | Description                          |
|-------------|--------------------------------------|
| `startFrom` | Start time of the slot               |
| `type`      | `BUSY` or `AVAILABLE`                |
| `personId`  | Owner of the slot                    |
| `meetingId` | Associated meeting (if any)          |

Slot duration is configured via `application.yaml`:

```yaml
timeslot:
  durationMins: 15  # must satisfy: 60 % durationMins == 0
```

> ⚠️ The system requires `60 % timeSlotDuration == 0` (e.g. 5, 10, 15, 20, 30, 60 minutes).

### Meetings

A meeting has:
- `startDateTime` and `endDateTime`
- A list of participants

In v1, attendance of **all participants is required** — there are no optional attendees.

### Meeting Creation Flow

1. Collect the requested time range and participant list
2. Lock the per-person slot buckets (by `personId`) to prevent race conditions
3. Validate that all participants have available slots covering the meeting window
4. Convert those slots to `BUSY` and create the meeting record

> The locking strategy in v1 is a simple per-`personId` bucket lock for straightforward implementation.

## Getting Started

```bash
docker-compose up
```

See [Configuration](#configuration) for available options.

## Configuration

| Property                | Default | Description                                      |
|-------------------------|---------|--------------------------------------------------|
| `timeslot.durationMins` | `15`    | Duration of each time slot (must divide 60 evenly) |

## API Reference

_Coming soon — see code or Swagger UI at `/swagger-ui.html` when running locally._

## Future Improvements

- Optional / required attendee distinction
- Recurring time slots
- Conflict resolution and partial availability
- Persistent storage (current implementation is in-memory)