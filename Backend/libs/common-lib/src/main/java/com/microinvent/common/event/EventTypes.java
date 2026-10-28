package com.microinvent.common.event;

/** Tên exchange và routing key dùng chung. */
public final class EventTypes {

    public static final String EXCHANGE = "inventory.events";

    public static final String RECEIPT_CONFIRMED = "receipt.confirmed";
    public static final String EXPORT_EXPORTED = "export.exported";
    public static final String RENTAL_CREATED = "rental.created";
    public static final String RENTAL_RETURNED = "rental.returned";
    public static final String RETURN_COMPLETED = "return.completed";
    public static final String TRANSFER_SUBMITTED = "transfer.submitted";
    public static final String TRANSFER_SHIPPED = "transfer.shipped";
    public static final String TRANSFER_RECEIVED = "transfer.received";
    public static final String TRANSFER_DISCREPANCY = "transfer.discrepancy";
    public static final String STOCK_CHANGED = "stock.changed";
    public static final String STOCK_LOW = "stock.low";
    public static final String HOLD_EXPIRED = "hold.expired";
    public static final String AUDIT_LOG = "audit.log";

    private EventTypes() {
    }
}
