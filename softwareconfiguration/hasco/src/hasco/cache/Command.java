package hasco.cache;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
	"command",
	"inputs",
	"outputs"
})
public class Command {

	@JsonProperty("command")
	protected String name;
	
	@JsonProperty("inputs")
	protected Map<String, String> inputs;
	@JsonProperty("outputs")
	protected Map<String, String> outputs;
	
	public void execute(CacheObject context) {
		
	}
	
	
	
	
}
