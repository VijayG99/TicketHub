# S7: choreography or orchestration? (decision note)

**Question.** The payment flow now spans three services and six events. Who knows the whole flow?

## What TicketHub does today: choreography
Each service reacts to events and emits new ones. No one holds the full picture.
`PaymentSucceeded` -> Booking confirms -> `BookingConfirmed` -> Notification mails.
Failure path: `BookingRejected` -> Payment refunds -> `PaymentRefunded` -> Booking marks the saga done.

## The alternative: orchestration (prototype described, not built)
A coordinator (for example inside Booking, a `BookingSaga` class with a state table) would *send commands*:
`ChargePayment`, wait; on success `ConfirmBooking`, `SendEmail`; on failure `ReleaseSeats`, `RefundPayment`.
It keeps one row per booking: `STARTED -> PAYMENT_PENDING -> CONFIRMED / COMPENSATING -> FAILED`.

## What we built to get orchestration's best feature without its cost
Booking already owns the booking, so it records a **saga state** (`PAYMENT_PENDING, CONFIRMED, COMPENSATING, COMPENSATED, FAILED`)
and a **saga log** table. `GET /bookings/{id}/saga` answers "where is booking X and why?" with a plain sentence and a timeline,
without turning Booking into a central controller.

## Comparison
| | Choreography (now) | Orchestration |
|---|---|---|
| Visibility of the flow | spread over services; needs the saga log/trace to see it | one place shows the whole flow |
| Coupling | low: services only know event contracts | higher: coordinator knows every participant |
| Complexity | grows with every new event listener (hard to follow at 10+ events) | grows in the coordinator, but readable |
| Failure handling | each service handles its own compensation; easy to forget a case | coordinator decides compensation centrally |
| Single point of failure | none | the coordinator must itself be reliable (outbox + persisted state) |
| Changing the flow | touch several services | change one class |

## Decision
**Keep choreography for TicketHub.** The flow has only two participants that need compensation (Booking, Payment) and a
fire-and-forget third (Notification). The visibility problem is solved cheaply by the saga state + log. Reconsider
orchestration when a fourth step with its own undo appears (for example loyalty points, seat-map sync, invoicing), because
the number of cross-service event paths then grows faster than anyone can hold in their head.
