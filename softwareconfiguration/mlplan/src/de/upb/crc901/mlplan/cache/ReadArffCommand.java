package de.upb.crc901.mlplan.cache;

import java.io.IOException;

import hasco.cache.CacheObject;
import hasco.cache.Command;
import jaicore.ml.openml.OpenMLHelper;
import weka.core.Instances;

public class ReadArffCommand extends Command{

	
	public ReadArffCommand(String provider, String id, String dataName) {
		inputs.put("provider", provider);
		inputs.put("id", id);
		outputs.put("dataName", dataName);
	}
	
	
	@Override
	public void execute(CacheObject context) {
		if(inputs.get("provider").equals("openml.org")) {
			OpenMLHelper.setApiKey("4350e421cdc16404033ef1812ea38c01");
			Instances data = null;
			try {
				data = OpenMLHelper.getInstancesById(Integer.parseInt(inputs.get("id")));
			} catch (NumberFormatException | IOException e) {
				// TODO abort
				e.printStackTrace();
			}
			context.getVariables().put(outputs.get("dataName"), data);
		}
	}

	
}
