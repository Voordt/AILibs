package hasco.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
	"namespace",
	"routine"
})
public class CacheObject {
	
	@JsonProperty("reproducable")
	private boolean reproducable = true;
	
	/** Variables used in this cache */
	@JsonIgnore
	private Map<String, Object> variables;
	
	@JsonProperty("namespace")
	private String namespace;
	
	@JsonProperty("routine")
	private List<Command> commands;
	
	
	
	@JsonIgnore
	public Map<String, Object> getVariables() {
		return variables;
	}
	
	
	@JsonProperty("reproducable")
	public boolean isReproducable() {
		return reproducable;
	}
	
}
