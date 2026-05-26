package edu.depaul.se331.chatbot.model;

/**
 * The JSON body sent by the browser to the summarize endpoint.
 *
 * POST /api/summarize
 * { "text": "A long block of text to summarize..." }
 */
public class SummarizeRequest {

    private String text;

    public SummarizeRequest() {}

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
