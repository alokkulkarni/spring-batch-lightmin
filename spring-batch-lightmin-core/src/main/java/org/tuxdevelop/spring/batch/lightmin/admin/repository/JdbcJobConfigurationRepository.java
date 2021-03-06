package org.tuxdevelop.spring.batch.lightmin.admin.repository;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.repository.dao.AbstractJdbcBatchMetadataDao;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.tuxdevelop.spring.batch.lightmin.admin.domain.*;
import org.tuxdevelop.spring.batch.lightmin.exception.NoSuchJobConfigurationException;
import org.tuxdevelop.spring.batch.lightmin.exception.NoSuchJobException;
import org.tuxdevelop.spring.batch.lightmin.exception.SpringBatchLightminApplicationException;
import org.tuxdevelop.spring.batch.lightmin.util.ParameterParser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Marcel Becker
 * @since 0.1
 */
@Slf4j
public class JdbcJobConfigurationRepository implements JobConfigurationRepository, InitializingBean {

    private final JdbcTemplate jdbcTemplate;
    private final String tablePrefix;
    private final JobConfigurationDAO jobConfigurationDAO;
    private final JobSchedulerConfigurationDAO jobSchedulerConfigurationDAO;
    private final JobConfigurationParameterDAO jobConfigurationParameterDAO;
    private final JobListenerConfigurationDAO jobListenerConfigurationDAO;

    public JdbcJobConfigurationRepository(final JdbcTemplate jdbcTemplate, final String tablePrefix, final String schema) {
        this.jdbcTemplate = jdbcTemplate;
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            this.tablePrefix = tablePrefix;
        } else {
            this.tablePrefix = AbstractJdbcBatchMetadataDao.DEFAULT_TABLE_PREFIX;
        }
        this.jobSchedulerConfigurationDAO = new JobSchedulerConfigurationDAO(jdbcTemplate, tablePrefix, schema);
        this.jobConfigurationDAO = new JobConfigurationDAO(jdbcTemplate, tablePrefix, schema);
        this.jobConfigurationParameterDAO = new JobConfigurationParameterDAO(jdbcTemplate, tablePrefix, schema);
        this.jobListenerConfigurationDAO = new JobListenerConfigurationDAO(jdbcTemplate, tablePrefix, schema);
    }

    @Override
    public JobConfiguration getJobConfiguration(final Long jobConfigurationId) throws NoSuchJobConfigurationException {
        if (checkJobConfigurationExists(jobConfigurationId)) {
            final JobConfiguration jobConfiguration = jobConfigurationDAO.getById(jobConfigurationId);
            jobSchedulerConfigurationDAO.attachJobSchedulerConfiguration(jobConfiguration);
            jobListenerConfigurationDAO.attachJobListenerConfiguration(jobConfiguration);
            jobConfigurationParameterDAO.attachParameters(jobConfiguration);
            return jobConfiguration;
        } else {
            final String message = "No jobConfiguration could be found for id:" + jobConfigurationId;
            log.error(message);
            throw new NoSuchJobConfigurationException(message);
        }
    }

    @Override
    public Collection<JobConfiguration> getJobConfigurations(final String jobName) throws NoSuchJobException {
        if (checkJobConfigurationExists(jobName)) {
            final List<JobConfiguration> jobConfigurations = jobConfigurationDAO.getByJobName(jobName);
            for (final JobConfiguration jobConfiguration : jobConfigurations) {
                jobSchedulerConfigurationDAO.attachJobSchedulerConfiguration(jobConfiguration);
                jobListenerConfigurationDAO.attachJobListenerConfiguration(jobConfiguration);
                jobConfigurationParameterDAO.attachParameters(jobConfiguration);
            }
            return jobConfigurations;
        } else {
            final String message = "No jobConfiguration could be found for jobName:" + jobName;
            log.error(message);
            throw new NoSuchJobException(message);
        }
    }

    @Override
    public JobConfiguration add(final JobConfiguration jobConfiguration) {
        final Long jobConfigurationId = jobConfigurationDAO.add(jobConfiguration);
        jobConfiguration.setJobConfigurationId(jobConfigurationId);
        if (jobConfiguration.getJobSchedulerConfiguration() != null) {
            jobSchedulerConfigurationDAO.add(jobConfiguration);
        }
        if (jobConfiguration.getJobListenerConfiguration() != null) {
            jobListenerConfigurationDAO.add(jobConfiguration);
        }
        jobConfigurationParameterDAO.add(jobConfiguration);
        return jobConfiguration;
    }

    @Override
    public JobConfiguration update(final JobConfiguration jobConfiguration) throws NoSuchJobConfigurationException {
        final Long jobConfigurationId = jobConfiguration.getJobConfigurationId();
        if (checkJobConfigurationExists(jobConfigurationId)) {
            jobConfigurationDAO.update(jobConfiguration);
            if (jobConfiguration.getJobSchedulerConfiguration() != null) {
                jobSchedulerConfigurationDAO.update(jobConfiguration);
            }
            if (jobConfiguration.getJobListenerConfiguration() != null) {
                jobListenerConfigurationDAO.update(jobConfiguration);
            }
            jobConfigurationParameterDAO.delete(jobConfigurationId);
            jobConfigurationParameterDAO.add(jobConfiguration);
            return jobConfiguration;
        } else {
            final String message = "No jobConfiguration could be found for id:" + jobConfiguration;
            log.error(message);
            throw new NoSuchJobConfigurationException(message);
        }
    }

    @Override
    public void delete(final JobConfiguration jobConfiguration) throws NoSuchJobConfigurationException {
        final Long jobConfigurationId = jobConfiguration.getJobConfigurationId();
        if (checkJobConfigurationExists(jobConfigurationId)) {
            jobConfigurationParameterDAO.delete(jobConfigurationId);
            jobSchedulerConfigurationDAO.delete(jobConfigurationId);
            jobListenerConfigurationDAO.delete(jobConfigurationId);
            jobConfigurationDAO.delete(jobConfigurationId);
        } else {
            final String message = "No jobConfiguration could be found for id:" + jobConfiguration;
            log.error(message);
            throw new NoSuchJobConfigurationException(message);
        }
    }

    @Override
    public Collection<JobConfiguration> getAllJobConfigurations() {
        final List<JobConfiguration> jobConfigurations = jobConfigurationDAO.getAll();
        for (final JobConfiguration jobConfiguration : jobConfigurations) {
            jobSchedulerConfigurationDAO.attachJobSchedulerConfiguration(jobConfiguration);
            jobListenerConfigurationDAO.attachJobListenerConfiguration(jobConfiguration);
            jobConfigurationParameterDAO.attachParameters(jobConfiguration);
        }
        return jobConfigurations;
    }

    @Override
    public Collection<JobConfiguration> getAllJobConfigurationsByJobNames(final Collection<String> jobNames) {
        final List<JobConfiguration> jobConfigurations = jobConfigurationDAO.getAllByJobNames(jobNames);
        for (final JobConfiguration jobConfiguration : jobConfigurations) {
            jobSchedulerConfigurationDAO.attachJobSchedulerConfiguration(jobConfiguration);
            jobListenerConfigurationDAO.attachJobListenerConfiguration(jobConfiguration);
            jobConfigurationParameterDAO.attachParameters(jobConfiguration);
        }
        return jobConfigurations;
    }

    @Override
    public void afterPropertiesSet() {
        assert jdbcTemplate != null;
        assert tablePrefix != null;
    }

	/*
     * -------------------------- HELPER CLASSES AND METHODS -------------------
	 */

    private Boolean checkJobConfigurationExists(final Long jobConfigurationId) {
        return jobConfigurationDAO.getJobConfigurationIdCount(jobConfigurationId) > 0;
    }

    private Boolean checkJobConfigurationExists(final String jobName) {
        return jobConfigurationDAO.getJobNameCount(jobName) > 0;
    }

    /**
     *
     */
    private static class JobConfigurationDAO {

        private static final String TABLE_NAME = "%sJOB_CONFIGURATION";

        private static final String GET_JOB_CONFIGURATION_QUERY = "SELECT * FROM " + TABLE_NAME + " WHERE "
                + JobConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String GET_JOB_CONFIGURATIONS_BY_JOB_NAME_QUERY = "SELECT * FROM " + TABLE_NAME
                + " WHERE " + JobConfigurationDomain.JOB_NAME + " = ?";

        private static final String UPDATE_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
                + JobConfigurationDomain.JOB_NAME + "" + " = ? , " + JobConfigurationDomain.JOB_INCREMENTER
                + " = ? WHERE " + JobConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE "
                + JobConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String GET_JOB_CONFIGURATION_ID_COUNT_STATEMENT = "SELECT COUNT(1) FROM " + TABLE_NAME
                + " WHERE" + " " + JobConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String GET_JOB_NAME_COUNT_STATEMENT = "SELECT COUNT(1) FROM " + TABLE_NAME + " WHERE"
                + " " + JobConfigurationDomain.JOB_NAME + " = ?";

        private static final String GET_ALL_JOB_CONFIGURATION_QUERY = "SELECT * FROM " + TABLE_NAME;

        private static final String GET_ALL_JOB_CONFIGURATION_BY_JOB_NAMES_QUERY = "SELECT * FROM " + TABLE_NAME + " " +
                "WHERE " + JobConfigurationDomain.JOB_NAME + " IN (%s)";

        private final JdbcTemplate jdbcTemplate;
        private final SimpleJdbcInsert simpleJdbcInsert;
        private final String tablePrefix;

        JobConfigurationDAO(final JdbcTemplate jdbcTemplate, final String tablePrefix, final String schema) {
            this.jdbcTemplate = jdbcTemplate;
            this.tablePrefix = tablePrefix;
            this.simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate)
                    .withSchemaName(schema)
                    .withTableName(String.format(TABLE_NAME, tablePrefix)).usingGeneratedKeyColumns(
                            JobConfigurationDomain.JOB_CONFIGURATION_ID);
        }

        public Long add(final JobConfiguration jobConfiguration) {
            final Map<String, ?> keyValues = map(jobConfiguration);
            final Number key = simpleJdbcInsert.executeAndReturnKey(keyValues);
            return key.longValue();
        }

        JobConfiguration getById(final Long jobConfigurationId) {
            final String sql = String.format(GET_JOB_CONFIGURATION_QUERY, tablePrefix);
            return jdbcTemplate.queryForObject(sql, new JobConfigurationRowMapper(), jobConfigurationId);
        }

        List<JobConfiguration> getByJobName(final String jobName) {
            final String sql = String.format(GET_JOB_CONFIGURATIONS_BY_JOB_NAME_QUERY, tablePrefix);
            return jdbcTemplate.query(sql, new JobConfigurationRowMapper(), jobName);
        }

        public void update(final JobConfiguration jobConfiguration) {
            final String sql = String.format(UPDATE_STATEMENT, tablePrefix);
            jdbcTemplate.update(
                    sql,
                    new Object[]{jobConfiguration.getJobName(),
                            jobConfiguration.getJobIncrementer().getIncrementerIdentifier(),
                            jobConfiguration.getJobConfigurationId()}, new int[]{Types.VARCHAR, Types.VARCHAR,
                            Types.NUMERIC});
        }

        public void delete(final Long jobConfigurationId) {
            final String sql = String.format(DELETE_STATEMENT, tablePrefix);
            jdbcTemplate.update(sql, new Object[]{jobConfigurationId}, new int[]{Types.NUMERIC});
        }

        Long getJobConfigurationIdCount(final Long jobConfiguration) {
            final String sql = String.format(GET_JOB_CONFIGURATION_ID_COUNT_STATEMENT, tablePrefix);
            return jdbcTemplate.queryForObject(sql, new Object[]{jobConfiguration}, new int[]{Types.NUMERIC},
                    Long.class);
        }

        Long getJobNameCount(final String jobName) {
            final String sql = String.format(GET_JOB_NAME_COUNT_STATEMENT, tablePrefix);
            return jdbcTemplate.queryForObject(sql, new Object[]{jobName}, new int[]{Types.VARCHAR}, Long.class);
        }

        List<JobConfiguration> getAll() {
            final String sql = String.format(GET_ALL_JOB_CONFIGURATION_QUERY, tablePrefix);
            return jdbcTemplate.query(sql, new JobConfigurationRowMapper());
        }

        List<JobConfiguration> getAllByJobNames(final Collection<String> jobNames) {
            final String inParameters = parseInCollection(jobNames);
            final String sql = String.format(GET_ALL_JOB_CONFIGURATION_BY_JOB_NAMES_QUERY, tablePrefix, inParameters);
            return jdbcTemplate
                    .query(sql, new JobConfigurationRowMapper(), jobNames.toArray());
        }

        private Map<String, Object> map(final JobConfiguration jobConfiguration) {
            final Map<String, Object> keyValues = new HashMap<>();
            keyValues.put(JobConfigurationDomain.JOB_NAME, jobConfiguration.getJobName());
            keyValues.put(JobConfigurationDomain.JOB_INCREMENTER, jobConfiguration.getJobIncrementer()
                    .getIncrementerIdentifier());
            if (jobConfiguration.getJobConfigurationId() != null) {
                keyValues.put(JobConfigurationDomain.JOB_CONFIGURATION_ID, jobConfiguration.getJobConfigurationId());
            }
            return keyValues;
        }

        private String parseInCollection(final Collection<String> inParameters) {
            final StringBuilder stringBuilder = new StringBuilder();
            final Iterator<String> iterator = inParameters.iterator();
            while (iterator.hasNext()) {
                stringBuilder.append("?");
                iterator.next();
                if (iterator.hasNext()) {
                    stringBuilder.append(",");
                }
            }
            return stringBuilder.toString();
        }
    }

    /**
     *
     */
    private static class JobListenerConfigurationDAO {

        private static final String TABLE_NAME = "%sJOB_LISTENER_CONFIGURATION";

        private static final String GET_JOB_LISTENER_QUERY = "SELECT * FROM " + TABLE_NAME + " WHERE "
                + JobListenerConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE "
                + JobListenerConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String UPDATE_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
                + JobListenerConfigurationDomain.LISTENER_TYPE + " = ? , "
                + JobListenerConfigurationDomain.FILE_PATTERN + " = ? , "
                + JobListenerConfigurationDomain.SOURCE_FOLDER + " = ? , "
                + JobListenerConfigurationDomain.TASK_EXECUTOR_TYPE + " = ? , "
                + JobListenerConfigurationDomain.POLLER_PERIOD + " = ? , "
                + JobListenerConfigurationDomain.BEAN_NAME + " = ? , "
                + JobListenerConfigurationDomain.STATUS + " = ? WHERE "
                + JobListenerConfigurationDomain.JOB_CONFIGURATION_ID + " = ? ";


        private final JdbcTemplate jdbcTemplate;
        private final SimpleJdbcInsert simpleJdbcInsert;
        private final String tablePrefix;

        JobListenerConfigurationDAO(final JdbcTemplate jdbcTemplate, final String tablePrefix, final String schema) {
            this.jdbcTemplate = jdbcTemplate;
            this.simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate)
                    .withSchemaName(schema)
                    .withTableName(String.format(TABLE_NAME, tablePrefix))
                    .usingGeneratedKeyColumns(JobListenerConfigurationDomain.ID);
            this.tablePrefix = tablePrefix;
        }

        public Long add(final JobConfiguration jobConfiguration) {
            final Map<String, ?> keyValues = map(jobConfiguration);
            final Number key = simpleJdbcInsert.executeAndReturnKey(keyValues);
            return key.longValue();
        }

        public void update(final JobConfiguration jobConfiguration) {
            final JobListenerConfiguration jobListenerConfiguration = jobConfiguration.getJobListenerConfiguration();
            final String sql = String.format(UPDATE_STATEMENT, tablePrefix);
            final Object[] objects = {
                    jobListenerConfiguration.getJobListenerType().getId(),
                    jobListenerConfiguration.getFilePattern(),
                    jobListenerConfiguration.getSourceFolder(),
                    jobListenerConfiguration.getTaskExecutorType().getId(),
                    jobListenerConfiguration.getPollerPeriod(),
                    jobListenerConfiguration.getBeanName(),
                    jobListenerConfiguration.getListenerStatus().getValue(),
                    jobConfiguration.getJobConfigurationId()
            };
            final int[] types = {
                    Types.INTEGER,
                    Types.VARCHAR,
                    Types.VARCHAR,
                    Types.INTEGER,
                    Types.NUMERIC,
                    Types.VARCHAR,
                    Types.VARCHAR,
                    Types.NUMERIC
            };
            jdbcTemplate.update(sql, objects, types);
        }

        public void delete(final Long jobConfigurationId) {
            final String sql = String.format(DELETE_STATEMENT, tablePrefix);
            jdbcTemplate.update(sql, new Object[]{jobConfigurationId}, new int[]{Types.NUMERIC});
        }

        void attachJobListenerConfiguration(final JobConfiguration jobConfiguration) {
            final String sql = String.format(GET_JOB_LISTENER_QUERY, tablePrefix);
            try {
                final JobListenerConfiguration jobListenerConfiguration = jdbcTemplate.queryForObject(sql,
                        new JobListenerConfigurationRowMapper(), jobConfiguration.getJobConfigurationId());
                jobConfiguration.setJobListenerConfiguration(jobListenerConfiguration);
            } catch (final DataAccessException e) {
                log.debug("Could not get JobListenerConfiguration for jobConfigurationId {}", jobConfiguration.getJobConfigurationId());
            }
        }

        private Map<String, Object> map(final JobConfiguration jobConfiguration) {
            final JobListenerConfiguration jobListenerConfiguration = jobConfiguration.getJobListenerConfiguration();
            final Map<String, Object> keyValues = new HashMap<>();
            keyValues.put(JobListenerConfigurationDomain.LISTENER_TYPE, jobListenerConfiguration.getJobListenerType().getId());
            keyValues.put(JobListenerConfigurationDomain.SOURCE_FOLDER, jobListenerConfiguration.getSourceFolder());
            keyValues.put(JobListenerConfigurationDomain.FILE_PATTERN, jobListenerConfiguration.getFilePattern());
            keyValues.put(JobListenerConfigurationDomain.STATUS, jobListenerConfiguration.getListenerStatus().getValue());
            keyValues.put(JobListenerConfigurationDomain.JOB_CONFIGURATION_ID, jobConfiguration.getJobConfigurationId());
            keyValues.put(JobListenerConfigurationDomain.BEAN_NAME, jobListenerConfiguration.getBeanName());
            keyValues.put(JobListenerConfigurationDomain.TASK_EXECUTOR_TYPE, jobListenerConfiguration.getTaskExecutorType().getId());
            keyValues.put(JobListenerConfigurationDomain.POLLER_PERIOD, jobListenerConfiguration.getPollerPeriod());
            return keyValues;
        }

    }


    /**
     *
     */
    private static class JobSchedulerConfigurationDAO {

        private static final String TABLE_NAME = "%sJOB_SCHEDULER_CONFIGURATION";

        private static final String GET_JOB_SCHEDULER_QUERY = "SELECT * FROM " + TABLE_NAME + " WHERE "
                + JobSchedulerConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String UPDATE_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
                + JobSchedulerConfigurationDomain.CRON_EXPRESSION + " = ? , "
                + JobSchedulerConfigurationDomain.FIXED_DELAY + " = ? , "
                + JobSchedulerConfigurationDomain.INITIAL_DELAY + " = ? , "
                + JobSchedulerConfigurationDomain.SCHEDULER_TYPE + " = ?, "
                + JobSchedulerConfigurationDomain.TASK_EXECUTOR_TYPE + " = ?, "
                + JobSchedulerConfigurationDomain.BEAN_NAME + " = ?, "
                + JobSchedulerConfigurationDomain.STATUS + " = ? WHERE "
                + JobSchedulerConfigurationDomain.JOB_CONFIGURATION_ID + " = ? ";

        private static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE "
                + JobSchedulerConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private final JdbcTemplate jdbcTemplate;
        private final SimpleJdbcInsert simpleJdbcInsert;
        private final String tablePrefix;

        JobSchedulerConfigurationDAO(final JdbcTemplate jdbcTemplate, final String tablePrefix, final String schema) {
            this.jdbcTemplate = jdbcTemplate;
            this.tablePrefix = tablePrefix;
            this.simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate)
                    .withSchemaName(schema)
                    .withTableName(String.format(TABLE_NAME, tablePrefix))
                    .usingGeneratedKeyColumns(JobSchedulerConfigurationDomain.ID);
        }

        public Long add(final JobConfiguration jobConfiguration) {
            final Map<String, ?> keyValues = map(jobConfiguration);
            final Number key = simpleJdbcInsert.executeAndReturnKey(keyValues);
            return key.longValue();
        }

        void attachJobSchedulerConfiguration(final JobConfiguration jobConfiguration) {
            final String sql = String.format(GET_JOB_SCHEDULER_QUERY, tablePrefix);
            try {
                final JobSchedulerConfiguration jobSchedulerConfiguration = jdbcTemplate.queryForObject(sql,
                        new JobSchedulerConfigurationRowMapper(), jobConfiguration.getJobConfigurationId());
                jobConfiguration.setJobSchedulerConfiguration(jobSchedulerConfiguration);
            } catch (final DataAccessException e) {
                log.debug("Clound not get JobSchedulerConfiguration for jobConfigurationId {}", jobConfiguration.getJobConfigurationId());
            }
        }

        public void update(final JobConfiguration jobConfiguration) {
            final JobSchedulerConfiguration jobSchedulerConfiguration = jobConfiguration.getJobSchedulerConfiguration();
            final String sql = String.format(UPDATE_STATEMENT, tablePrefix);
            final Object[] parameters = {
                    jobSchedulerConfiguration.getCronExpression(),
                    jobSchedulerConfiguration.getFixedDelay(),
                    jobSchedulerConfiguration.getInitialDelay(),
                    jobSchedulerConfiguration.getJobSchedulerType().getId(),
                    jobSchedulerConfiguration.getTaskExecutorType().getId(),
                    jobSchedulerConfiguration.getBeanName(),
                    jobSchedulerConfiguration.getSchedulerStatus().getValue(),
                    jobConfiguration.getJobConfigurationId()};
            final int[] types = {
                    Types.VARCHAR,
                    Types.NUMERIC,
                    Types.NUMERIC,
                    Types.NUMERIC,
                    Types.NUMERIC,
                    Types.VARCHAR,
                    Types.VARCHAR,
                    Types.NUMERIC};
            jdbcTemplate.update(sql, parameters, types);
        }

        public void delete(final Long jobConfigurationId) {
            final String sql = String.format(DELETE_STATEMENT, tablePrefix);
            jdbcTemplate.update(sql, new Object[]{jobConfigurationId}, new int[]{Types.NUMERIC});
        }

        private Map<String, Object> map(final JobConfiguration jobConfiguration) {
            final JobSchedulerConfiguration jobSchedulerConfiguration = jobConfiguration.getJobSchedulerConfiguration();
            final Map<String, Object> keyValues = new HashMap<>();
            keyValues.put(JobSchedulerConfigurationDomain.JOB_CONFIGURATION_ID,
                    jobConfiguration.getJobConfigurationId());
            keyValues.put(JobSchedulerConfigurationDomain.CRON_EXPRESSION,
                    jobSchedulerConfiguration.getCronExpression());
            keyValues.put(JobSchedulerConfigurationDomain.INITIAL_DELAY, jobSchedulerConfiguration.getInitialDelay());
            keyValues.put(JobSchedulerConfigurationDomain.FIXED_DELAY, jobSchedulerConfiguration.getFixedDelay());
            keyValues.put(JobSchedulerConfigurationDomain.SCHEDULER_TYPE, jobSchedulerConfiguration
                    .getJobSchedulerType().getId());
            keyValues.put(JobSchedulerConfigurationDomain.TASK_EXECUTOR_TYPE, jobSchedulerConfiguration
                    .getTaskExecutorType().getId());
            keyValues.put(JobSchedulerConfigurationDomain.BEAN_NAME, jobSchedulerConfiguration.getBeanName());
            keyValues.put(JobSchedulerConfigurationDomain.STATUS,
                    jobSchedulerConfiguration.getSchedulerStatus().getValue());
            return keyValues;
        }
    }

    /**
     *
     */
    private static class JobConfigurationParameterDAO {

        private static final String TABLE_NAME = "%sJOB_CONFIGURATION_PARAMETERS";

        private static final String GET_JOB_PARAMETERS_QUERY = "SELECT * FROM " + TABLE_NAME + " WHERE "
                + JobSchedulerConfigurationDomain.JOB_CONFIGURATION_ID + " = ?";

        private static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE "
                + JobConfigurationParameterDomain.JOB_CONFIGURATION_ID + " = ? ";

        private final JdbcTemplate jdbcTemplate;
        private final SimpleJdbcInsert simpleJdbcInsert;
        private final String tablePrefix;
        private final DateFormat dateFormat;

        JobConfigurationParameterDAO(final JdbcTemplate jdbcTemplate, final String tablePrefix, final String schema) {
            this.jdbcTemplate = jdbcTemplate;
            this.tablePrefix = tablePrefix;
            this.simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate)
                    .withSchemaName(schema)
                    .withTableName(String.format(TABLE_NAME, tablePrefix))
                    .usingGeneratedKeyColumns(JobConfigurationParameterDomain.ID);
            this.dateFormat = new SimpleDateFormat(ParameterParser.DATE_FORMAT_WITH_TIMESTAMP);
        }

        public void add(final JobConfiguration jobConfiguration) {
            final Long jobConfigurationId = jobConfiguration.getJobConfigurationId();
            final Map<String, Object> jobParameters = jobConfiguration.getJobParameters();
            if (jobParameters != null) {
                for (final Map.Entry<String, Object> jobParameter : jobParameters.entrySet()) {
                    final JobConfigurationParameter jobConfigurationParameter = createJobConfigurationParameter(
                            jobParameter.getKey(), jobParameter.getValue());
                    final String key = jobConfigurationParameter.getParameterName();
                    final String value = jobConfigurationParameter.getParameterValue();
                    final Long clazzType = jobConfigurationParameter.getParameterType();
                    final Map<String, Object> parameters = new HashMap<>();
                    parameters.put(JobConfigurationParameterDomain.JOB_CONFIGURATION_ID, jobConfigurationId);
                    parameters.put(JobConfigurationParameterDomain.PARAMETER_NAME, key);
                    parameters.put(JobConfigurationParameterDomain.PARAMETER_TYPE, clazzType);
                    parameters.put(JobConfigurationParameterDomain.PARAMETER_VALUE, value);
                    simpleJdbcInsert.executeAndReturnKey(parameters);
                }
            } else {
                log.info("JobParameters null, nothing to map!");
            }
        }

        void attachParameters(final JobConfiguration jobConfiguration) {
            final Long jobConfigurationId = jobConfiguration.getJobConfigurationId();
            final String sql = String.format(GET_JOB_PARAMETERS_QUERY, tablePrefix);
            final List<JobConfigurationParameter> jobConfigurationParameters = jdbcTemplate.query(sql,
                    new JobConfigurationParameterRowMapper(), jobConfigurationId);
            final Map<String, Object> jobParameters = new HashMap<>();
            for (final JobConfigurationParameter jobConfigurationParameter : jobConfigurationParameters) {
                final String key = jobConfigurationParameter.getParameterName();
                final Long typeId = jobConfigurationParameter.getParameterType();
                final String valueString = jobConfigurationParameter.getParameterValue();
                final ParameterType parameterType = ParameterType.getById(typeId);
                final Object value = createValue(valueString, parameterType);
                jobParameters.put(key, value);
            }
            jobConfiguration.setJobParameters(jobParameters);
        }

        public void delete(final Long jobConfigurationId) {
            final String sql = String.format(DELETE_STATEMENT, tablePrefix);
            jdbcTemplate.update(sql, new Object[]{jobConfigurationId}, new int[]{Types.NUMERIC});
        }

        private Object createValue(final String value, final ParameterType parameterType) {
            if (ParameterType.LONG.equals(parameterType)) {
                return Long.parseLong(value);
            } else if (ParameterType.STRING.equals(parameterType)) {
                return value;
            } else if (ParameterType.DOUBLE.equals(parameterType)) {
                return Double.parseDouble(value);
            } else if (ParameterType.DATE.equals(parameterType)) {
                try {
                    return this.dateFormat.parse(value);
                } catch (final ParseException e) {
                    throw new SpringBatchLightminApplicationException(e, e.getMessage());
                }
            } else {
                throw new SpringBatchLightminApplicationException("Unsupported ParameterType: "
                        + parameterType.getClazz().getSimpleName());
            }
        }

        private JobConfigurationParameter createJobConfigurationParameter(final String key, final Object value) {
            final JobConfigurationParameter jobConfigurationParameter = new JobConfigurationParameter();
            if (value instanceof Long) {
                jobConfigurationParameter.setParameterValue(value.toString());
                jobConfigurationParameter.setParameterType(ParameterType.LONG.getId());
            } else if (value instanceof String) {
                jobConfigurationParameter.setParameterValue(value.toString());
                jobConfigurationParameter.setParameterType(ParameterType.STRING.getId());
            } else if (value instanceof Date) {
                jobConfigurationParameter.setParameterValue(dateFormat
                        .format(ParameterParser.DATE_FORMAT_WITH_TIMESTAMP));
                jobConfigurationParameter.setParameterType(ParameterType.DATE.getId());
            } else if (value instanceof Double) {
                jobConfigurationParameter.setParameterValue(value.toString());
                jobConfigurationParameter.setParameterType(ParameterType.DOUBLE.getId());
            } else {
                throw new SpringBatchLightminApplicationException("Unknown jobParameterType: "
                        + value.getClass().getSimpleName());
            }
            jobConfigurationParameter.setParameterName(key);
            return jobConfigurationParameter;
        }
    }

    /**
     *
     */
    private static class JobSchedulerConfigurationRowMapper implements RowMapper<JobSchedulerConfiguration> {

        @Override
        public JobSchedulerConfiguration mapRow(final ResultSet resultSet, final int rowNum) throws SQLException {
            final JobSchedulerConfiguration jobSchedulerConfiguration = new JobSchedulerConfiguration();
            jobSchedulerConfiguration.setBeanName(resultSet.getString(JobSchedulerConfigurationDomain.BEAN_NAME));
            jobSchedulerConfiguration.setCronExpression(resultSet
                    .getString(JobSchedulerConfigurationDomain.CRON_EXPRESSION));
            jobSchedulerConfiguration.setFixedDelay(resultSet.getLong(JobSchedulerConfigurationDomain.FIXED_DELAY));
            jobSchedulerConfiguration.setInitialDelay(resultSet.getLong(JobSchedulerConfigurationDomain.INITIAL_DELAY));
            final JobSchedulerType jobSchedulerType = JobSchedulerType.getById(resultSet
                    .getLong(JobSchedulerConfigurationDomain.SCHEDULER_TYPE));
            jobSchedulerConfiguration.setJobSchedulerType(jobSchedulerType);
            final TaskExecutorType taskExecutorType = TaskExecutorType.getById(resultSet
                    .getLong(JobSchedulerConfigurationDomain.TASK_EXECUTOR_TYPE));
            jobSchedulerConfiguration.setTaskExecutorType(taskExecutorType);
            final SchedulerStatus schedulerStatus = SchedulerStatus.getByValue(resultSet.getString
                    (JobSchedulerConfigurationDomain.STATUS));
            jobSchedulerConfiguration.setSchedulerStatus(schedulerStatus);
            return jobSchedulerConfiguration;
        }
    }

    /**
     *
     */
    private static class JobConfigurationRowMapper implements RowMapper<JobConfiguration> {

        @Override
        public JobConfiguration mapRow(final ResultSet resultSet, final int rowNum) throws SQLException {
            final JobConfiguration jobConfiguration = new JobConfiguration();
            jobConfiguration.setJobConfigurationId(resultSet.getLong(JobConfigurationDomain.JOB_CONFIGURATION_ID));
            jobConfiguration.setJobName(resultSet.getString(JobConfigurationDomain.JOB_NAME));
            final JobIncrementer jobIncrementer = JobIncrementer.getByIdentifier(resultSet
                    .getString(JobConfigurationDomain.JOB_INCREMENTER));
            jobConfiguration.setJobIncrementer(jobIncrementer);
            return jobConfiguration;
        }
    }

    /**
     *
     */
    private static class JobConfigurationParameterRowMapper implements RowMapper<JobConfigurationParameter> {

        @Override
        public JobConfigurationParameter mapRow(final ResultSet resultSet, final int rowNum) throws SQLException {
            final JobConfigurationParameter jobConfigurationParameter = new JobConfigurationParameter();
            jobConfigurationParameter.setParameterName(resultSet
                    .getString(JobConfigurationParameterDomain.PARAMETER_NAME));
            jobConfigurationParameter.setParameterValue(resultSet
                    .getString(JobConfigurationParameterDomain.PARAMETER_VALUE));
            jobConfigurationParameter.setParameterType(resultSet
                    .getLong(JobConfigurationParameterDomain.PARAMETER_TYPE));
            return jobConfigurationParameter;
        }
    }

    private static class JobListenerConfigurationRowMapper implements RowMapper<JobListenerConfiguration> {

        @Override
        public JobListenerConfiguration mapRow(final ResultSet result, final int rowNum) throws SQLException {
            final JobListenerConfiguration jobListenerConfiguration = new JobListenerConfiguration();
            jobListenerConfiguration.setFilePattern(result.getString(JobListenerConfigurationDomain.FILE_PATTERN));
            jobListenerConfiguration.setSourceFolder(result.getString(JobListenerConfigurationDomain.SOURCE_FOLDER));
            jobListenerConfiguration.setBeanName(result.getString(JobListenerConfigurationDomain.BEAN_NAME));
            final JobListenerType jobListenerType = JobListenerType.getById(result.getLong(JobListenerConfigurationDomain.LISTENER_TYPE));
            jobListenerConfiguration.setJobListenerType(jobListenerType);
            final ListenerStatus listenerStatus = ListenerStatus.getByValue(result.getString(JobListenerConfigurationDomain.STATUS));
            jobListenerConfiguration.setListenerStatus(listenerStatus);
            final TaskExecutorType taskExecutorType = TaskExecutorType.getById(result.getLong(JobListenerConfigurationDomain.TASK_EXECUTOR_TYPE));
            jobListenerConfiguration.setTaskExecutorType(taskExecutorType);
            jobListenerConfiguration.setPollerPeriod(result.getLong(JobListenerConfigurationDomain.POLLER_PERIOD));
            return jobListenerConfiguration;
        }
    }

    @Data
    private static class JobConfigurationParameter {
        private String parameterName;
        private String parameterValue;
        private Long parameterType;

    }
}
