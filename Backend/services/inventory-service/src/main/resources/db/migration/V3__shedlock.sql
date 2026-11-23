-- ShedLock (T32): HoldExpiryJob chỉ chạy trên 1 instance inventory-service tại một thời điểm, theo từng tenant.
CREATE TABLE shedlock (
  name       VARCHAR(64)  NOT NULL PRIMARY KEY,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  locked_by  VARCHAR(255) NOT NULL
);
