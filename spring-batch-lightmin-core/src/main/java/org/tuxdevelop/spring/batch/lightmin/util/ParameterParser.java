package org.tuxdevelop.spring.batch.lightmin.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.tuxdevelop.spring.batch.lightmin.api.resource.common.ParameterType;
import org.tuxdevelop.spring.batch.lightmin.exception.SpringBatchLightminApplicationException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * @author Marcel Becker
 * @since 0.1
 * <p>
 * Utility class to parse a String representation of job Parameters to {@link org.springframework.batch.core
 * .JobParameters} and {@link java.util.Map} of String, Object.
 * </p>
 * <p>
 * It is also possible to map {@link org.springframework.batch.core.JobParameters} and {@link java.util.Map} of
 * String and Object to human readble String parameters
 * </p>
 */
@Slf4j
public final class ParameterParser {

    public static final String DATE_FORMAT_WITH_TIMESTAMP = "yyyy/MM/dd HH:mm:ss:SSS";
    public static final String DATE_FORMAT = "yyyy/MM/dd";

    private static final SimpleDateFormat simpleDateFormatTimeStamp = new SimpleDateFormat(DATE_FORMAT_WITH_TIMESTAMP);

    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);

    private ParameterParser() {
    }

    /**
     * Maps {@link org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameters} to a readable String
     *
     * @param jobParameters {@link org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameters}
     * @return a human readble representation of the JobParamaters
     */
    public static String parseParametersToString(final org.tuxdevelop.spring.batch.lightmin.api.resource.common
            .JobParameters jobParameters) {
        final Map<String, org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameter> parameters = jobParameters.getParameters();
        final Map<String, Object> parameterMap = new HashMap<>();
        for (final Entry<String, org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameter> entry : parameters.entrySet()) {
            parameterMap.put(entry.getKey(), entry.getValue().getParameter());
        }
        return parseParameterMapToString(parameterMap);
    }

    /**
     * Maps key value pairs of job parameters to a readable String
     *
     * @param parametersMap parameter map of String, Object
     * @return a human readble representation of the parameter map
     */
    public static String parseParameterMapToString(final Map<String, Object> parametersMap) {
        final StringBuilder stringBuilder = new StringBuilder();
        if (parametersMap != null) {
            for (final Entry<String, Object> entry : parametersMap.entrySet()) {
                final String key = entry.getKey();
                final Object value = entry.getValue();
                final String valueType;
                final String valueString;
                if (value instanceof Long) {
                    valueType = "(Long)";
                    valueString = value.toString();
                } else if (value instanceof String) {
                    valueType = "(String)";
                    valueString = (String) value;
                } else if (value instanceof Double) {
                    valueType = "(Double)";
                    valueString = value.toString();
                } else if (value instanceof Date) {
                    valueType = "(Date)";
                    valueString = simpleDateFormatTimeStamp.format((Date) value);
                } else {
                    throw new SpringBatchLightminApplicationException("Unknown ParameterType:" + value.getClass().getName());
                }
                stringBuilder.append(key);
                stringBuilder.append(valueType);
                stringBuilder.append("=");
                stringBuilder.append(valueString);
                stringBuilder.append(",");
            }
        }
        final String tempParameters = stringBuilder.toString();
        final String result;
        if (!tempParameters.isEmpty() && tempParameters.length() >= 1) {
            result = tempParameters.substring(0, tempParameters.length() - 1);
        } else {
            result = tempParameters;
        }
        return result;
    }

    /**
     * maps a String a parameters to Spring Batch {@link org.springframework.batch.core.JobParameters}.
     * The String has to have the following format:
     * <ul>
     * <li>
     * name(type)=value, name(type2)=value2
     * </li>
     * </ul>
     * The name is the job parameter name, the type, the Java type to the value.
     * Following Types are supported
     * <ul>
     * <li>
     * {@link java.lang.String}
     * </li>
     * <li>
     * {@link java.lang.Long}
     * </li>
     * <li>
     * {@link java.lang.Double}
     * </li>
     * <li>
     * {@link java.util.Date}
     * </li>
     * </ul>
     *
     * @param parameters String of parameters
     * @return Spring Batch {@link org.springframework.batch.core.JobParameters}
     */
    public static JobParameters parseParametersToJobParameters(final String parameters) {
        final JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
        final Map<String, Object> parametersMap = parseParameters(parameters);
        for (final Entry<String, Object> entry : parametersMap.entrySet()) {
            final Object value = entry.getValue();
            if (value instanceof String) {
                jobParametersBuilder.addString(entry.getKey(), (String) value);
            } else if (value instanceof Long) {
                jobParametersBuilder.addLong(entry.getKey(), (Long) value);
            } else if (value instanceof Date) {
                jobParametersBuilder.addDate(entry.getKey(), (Date) value);
            } else if (value instanceof Double) {
                jobParametersBuilder.addDouble(entry.getKey(), (Double) value);
            } else {
                throw new SpringBatchLightminApplicationException("Unknown Parameter type:"
                        + value.getClass().getName());
            }
        }
        return jobParametersBuilder.toJobParameters();
    }

    /**
     * maps a String a parameters to  {@link org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameters}.
     * The String has to have the following format:
     * <ul>
     * <li>
     * name(type)=value, name(type2)=value2
     * </li>
     * </ul>
     * The name is the job parameter name, the type, the Java type to the value.
     * Following Types are supported
     * <ul>
     * <li>
     * {@link java.lang.String}
     * </li>
     * <li>
     * {@link java.lang.Long}
     * </li>
     * <li>
     * {@link java.lang.Double}
     * </li>
     * <li>
     * {@link java.util.Date}
     * </li>
     * </ul>
     *
     * @param parameters String of parameters
     * @return Spring Batch {@link org.springframework.batch.core.JobParameters}
     */
    public static org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameters parseParametersStringToJobParameters(final String parameters) {
        final org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameters jobParameters = new org
                .tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameters();
        final Map<String, Object> parametersMap = parseParameters(parameters);
        for (final Entry<String, Object> entry : parametersMap.entrySet()) {
            final Object value = entry.getValue();
            final org.tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameter jobParameter = new org
                    .tuxdevelop.spring.batch.lightmin.api.resource.common.JobParameter();
            if (value instanceof String) {
                jobParameter.setParameterType(ParameterType.STRING);
                jobParameter.setParameter(value);
            } else if (value instanceof Long) {
                jobParameter.setParameterType(ParameterType.LONG);
                jobParameter.setParameter(value);
            } else if (value instanceof Date) {
                jobParameter.setParameterType(ParameterType.DATE);
                jobParameter.setParameter(value);
            } else if (value instanceof Double) {
                jobParameter.setParameterType(ParameterType.DOUBLE);
                jobParameter.setParameter(value);
            } else {
                throw new SpringBatchLightminApplicationException("Unknown Parameter type:"
                        + value.getClass().getName());
            }
            jobParameters.getParameters().put(entry.getKey(), jobParameter);
        }
        return jobParameters;
    }

    /**
     * maps a parameters String to {@link java.util.Map} of String, Object
     *
     * @param parameters String representation of the paremeters
     * @return a {@link java.util.Map} of String, Object
     */
    public static Map<String, Object> parseParameters(final String parameters) {
        final Map<String, Object> parameterMap;
        if (parameters == null || parameters.isEmpty()) {
            log.info("parameters null or empty, nothing to map!");
            parameterMap = new HashMap<>();
        } else {
            final List<String> parameterList = splitParameters(parameters);
            parameterMap = evaluateParameters(parameterList);
        }
        return parameterMap;
    }

    /**
     * maps {@link org.springframework.batch.core.JobParameters} to a String representation
     *
     * @param jobParameters {@link org.springframework.batch.core.JobParameters} to map
     * @return a String representation of {@link org.springframework.batch.core.JobParameters}
     */
    public static String parseJobParametersToString(final JobParameters jobParameters) {
        final Map<String, JobParameter> jobParametersMap = jobParameters.getParameters();
        final Map<String, Object> paramatersMap = new HashMap<>();
        for (final Entry<String, JobParameter> entry : jobParametersMap.entrySet()) {
            paramatersMap.put(entry.getKey(), entry.getValue().getValue());
        }
        return parseParameterMapToString(paramatersMap);
    }

    private static List<String> splitParameters(final String parameters) {
        final List<String> parameterList = new LinkedList<>();
        final String[] parametersArray = parameters.split(",");
        Collections.addAll(parameterList, parametersArray);
        return parameterList;
    }

    private static Map<String, Object> evaluateParameters(final List<String> parameterList) {
        final Map<String, Object> parameters = new HashMap<>();
        for (final String parameter : parameterList) {
            final Entry<String, Object> entry = generateParameterEntry(parameter);
            parameters.put(entry.getKey(), entry.getValue());
        }
        return parameters;
    }

    static Entry<String, Object> generateParameterEntry(final String parameter) {
        final String key;
        final Object value;
        log.debug("parsing: " + parameter);
        final String[] firstSplit = parameter.split(Pattern.quote("("), 2);
        key = firstSplit[0].trim();
        log.debug("got key: " + key);
        final String tempString = firstSplit[1];
        log.debug("parsing second part: " + tempString);
        final String[] secondSplit = tempString.split(Pattern.quote(")"), 2);
        final String type = secondSplit[0].trim();
        log.debug("got type: " + type);
        final String tempSecondString = secondSplit[1];
        log.debug("parsing third part:" + tempSecondString);
        final String[] thirdSplit = tempSecondString.split("=", 2);
        final String valueString = thirdSplit[1];
        log.debug("got value: " + valueString);
        value = createValueInstance(type, valueString);
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    static Object createValueInstance(final String type, final String valueString) {
        final String upperCaseType = type.toUpperCase();
        final Object value;
        if (StringTypes.STRING.typeString.equals(upperCaseType)) {
            value = valueString;
        } else if (StringTypes.LONG.typeString.equals(upperCaseType)) {
            value = Long.parseLong(valueString);
        } else if (StringTypes.DOUBLE.typeString.equals(upperCaseType)) {
            value = Double.parseDouble(valueString);
        } else if (StringTypes.DATE.typeString.equals(upperCaseType)) {
            value = parseDate(valueString);
        } else {
            throw new SpringBatchLightminApplicationException("Unknown parameter Type: " + type);
        }
        return value;
    }

    static Date parseDate(final String dateString) {
        Date date;
        try {
            date = simpleDateFormatTimeStamp.parse(dateString);
        } catch (final ParseException e) {
            log.info("Could not parse date: " + dateString);
            try {
                date = simpleDateFormat.parse(dateString);
            } catch (final ParseException e1) {
                log.error(e.getMessage());
                throw new SpringBatchLightminApplicationException(e1, e1.getMessage());
            }
        }
        return date;
    }

    private enum StringTypes {

        STRING("STRING"), LONG("LONG"), DATE("DATE"), DOUBLE("DOUBLE");

        private String typeString;

        StringTypes(final String typeString) {
            this.typeString = typeString;
        }
    }

}
