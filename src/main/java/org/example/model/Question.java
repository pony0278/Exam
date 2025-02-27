package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class Question {
    @JsonProperty("QuestionNumber")
    private Integer QuestionNumber;
    @JsonProperty("CorrectAnswer")
    private String CorrectAnswer;
    @JsonProperty("QuestionText")
    private String QuestionText;
    @JsonProperty("Options")
    private Map<String, String> Options;
    @JsonProperty("Reference")
    private String Reference;

    // getters and setters
    public Integer getQuestionNumber() { return QuestionNumber; }
    public void setQuestionNumber(Integer questionNumber) { QuestionNumber = questionNumber; }

    public String getCorrectAnswer() { return CorrectAnswer; }
    public void setCorrectAnswer(String correctAnswer) { CorrectAnswer = correctAnswer; }

    public String getQuestionText() { return QuestionText; }
    public void setQuestionText(String questionText) { QuestionText = questionText; }

    public Map<String, String> getOptions() { return Options; }
    public void setOptions(Map<String, String> options) { Options = options; }

    public String getReference() { return Reference; }
    public void setReference(String reference) { Reference = reference; }
}