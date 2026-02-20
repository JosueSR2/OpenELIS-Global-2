package org.openelisglobal.middleware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyzerResultDTO {

    private String analyzerId;
    private String accessionNumber;
    private String testCode;
    private String testName;
    private String resultValue;
    private String units;
    private String status;
    private String instrumentName;
    private String resultDateTime;

    public AnalyzerResultDTO() {
        // Constructor vacío requerido por Jackson
    }

    public String getAnalyzerId() {
        return analyzerId;
    }

    public void setAnalyzerId(String analyzerId) {
        this.analyzerId = analyzerId;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getTestCode() {
        return testCode;
    }

    public void setTestCode(String testCode) {
        this.testCode = testCode;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getResultValue() {
        return resultValue;
    }

    public void setResultValue(String resultValue) {
        this.resultValue = resultValue;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getResultDateTime() {
        return resultDateTime;
    }

    public void setResultDateTime(String resultDateTime) {
        this.resultDateTime = resultDateTime;
    }

    @Override
    public String toString() {
        return "AnalyzerResultDTO{" +
                "analyzerId='" + analyzerId + '\'' +
                ", accessionNumber='" + accessionNumber + '\'' +
                ", testCode='" + testCode + '\'' +
                ", testName='" + testName + '\'' +
                ", resultValue='" + resultValue + '\'' +
                ", units='" + units + '\'' +
                ", status='" + status + '\'' +
                ", instrumentName='" + instrumentName + '\'' +
                ", resultDateTime='" + resultDateTime + '\'' +
                '}';
    }
}
