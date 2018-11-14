package de.upb.crc901.mlplan.cache;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import hasco.cache.CacheObject;
import hasco.cache.Command;
import jaicore.ml.WekaUtil;
import jaicore.ml.openml.OpenMLHelper;
import weka.core.Instances;

public class SplitCommand extends Command{

	
	public SplitCommand(String data, String ratios, String seed, String resultName) {
		inputs.put("data", data);
		inputs.put("ratios", ratios);
		inputs.put("seed", seed);
		outputs.put("resultName", resultName);
	}
	
	
	@Override
	public void execute(CacheObject context) {
		Instances data = (Instances)context.getVariables().get("data");
		// do the split
		String[] ratiosAsString = inputs.get("ratios").split(",");
		double[] ratios = new double[ratiosAsString.length];
		for (int i = 0; i < ratiosAsString.length; i++) {
			ratios[i] = Double.parseDouble(ratiosAsString[i]);	
		}
		List<Instances> partitions = WekaUtil.getStratifiedSplit(data, new Random(Integer.parseInt(inputs.get("seed"))), ratios);
		for (int i = 0; i < partitions.size(); i++) {
			context.getVariables().put(outputs.get("resultName") + "["+i+"]", partitions.get(i));		
		}
	}

}
