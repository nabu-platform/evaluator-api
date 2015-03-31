package be.nabu.libs.evaluator;

public class StringToken {
	
	private String content, preamble;
	private int start, end;
	
	public StringToken(String content, String preamble, int start, int end) {
		this.content = content;
		this.preamble = preamble;
		this.start = start;
		this.end = end;
	}
	
	public String getContent() {
		return content;
	}
	public int getStart() {
		return start;
	}
	public int getEnd() {
		return end;
	}

	public String getPreamble() {
		return preamble;
	}
}
