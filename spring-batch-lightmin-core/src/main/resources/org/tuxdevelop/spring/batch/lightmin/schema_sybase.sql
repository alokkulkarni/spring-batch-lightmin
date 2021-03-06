CREATE TABLE BATCH_JOB_CONFIGURATION (
  job_configuration_id NUMERIC IDENTITY PRIMARY KEY NOT NULL,
  job_name             UNIVARCHAR(255),
  job_incrementer      UNIVARCHAR(255)
)
GO

CREATE TABLE BATCH_JOB_SCHEDULER_CONFIGURATION (
  id                   NUMERIC IDENTITY PRIMARY KEY NOT NULL,
  job_configuration_id NUMERIC                      NOT NULL,
  scheduler_type       INT                          NOT NULL,
  cron_expression      UNIVARCHAR(255)              NULL,
  initial_delay        NUMERIC                      NULL,
  fixed_delay          NUMERIC                      NULL,
  task_executor_type   INT                          NOT NULL,
  bean_name            UNIVARCHAR(255)              NOT NULL,
  status               UNIVARCHAR(255)              NOT NULL,
  FOREIGN KEY (job_configuration_id) REFERENCES BATCH_JOB_CONFIGURATION (job_configuration_id)
)
GO

CREATE TABLE BATCH_JOB_LISTENER_CONFIGURATION (
  id                   NUMERIC IDENTITY PRIMARY KEY NOT NULL,
  job_configuration_id NUMERIC                      NOT NULL,
  listener_type        INT                          NOT NULL,
  source_folder        UNIVARCHAR(255)              NULL,
  file_pattern         UNIVARCHAR(255)              NULL,
  poller_period        NUMERIC                      NOT NULL,
  task_executor_type   INT                          NOT NULL,
  bean_name            UNIVARCHAR(255)              NOT NULL,
  status               UNIVARCHAR(255)              NOT NULL,
  FOREIGN KEY (job_configuration_id) REFERENCES BATCH_JOB_CONFIGURATION (job_configuration_id)
);

CREATE TABLE BATCH_JOB_CONFIGURATION_PARAMETERS (
  id                   NUMERIC IDENTITY PRIMARY KEY NOT NULL,
  job_configuration_id NUMERIC                      NOT NULL,
  parameter_name       UNIVARCHAR(255)              NOT NULL,
  parameter_value      UNIVARCHAR(255)              NOT NULL,
  parameter_type       INT                          NOT NULL,
  FOREIGN KEY (job_configuration_id) REFERENCES BATCH_JOB_CONFIGURATION (job_configuration_id)
)
GO

