package de.upb.crc901.mlplan.cache;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import hasco.cache.CacheObject;
import hasco.cache.Command;
import jaicore.ml.WekaUtil;
import jaicore.ml.evaluation.BasicMLEvaluator;
import jaicore.ml.openml.OpenMLHelper;
import weka.classifiers.Classifier;
import weka.core.Instances;

public class MonteCarloCrossValidationEvaluationCommand extends Command{

	
	private boolean canceled = false;
	
	public MonteCarloCrossValidationEvaluationCommand(String algorithm, String repeats, String trainingPortion) {
		inputs.put("algorithm", algorithm);
		inputs.put("repeats", repeats);
		inputs.put("trainingPortion", trainingPortion);
		
		// TODO input only strings for json or real values which are then transformed into json string??
		// reproduce?
	}
	
	@Override
	public void execute(CacheObject context) {
		// TODO check database
		
		Classifier pl = null; // how to get? by algorithm string or as parameter?
		
		if (pl == null) {
			//throw new IllegalArgumentException("Cannot compute score for null pipeline!");
		}

		int repeats = Integer.getInteger(inputs.get("repeats"));
		DescriptiveStatistics stats = new DescriptiveStatistics();
		Instances data = (Instances) context.getVariables().get(inputs.get("data"));
		double trainingPortion = Double.parseDouble(inputs.get("trainingPortion"));
		BasicMLEvaluator basicEvaluator = null;  // how to get this???
		
		
		/* perform random stratified split */
		//logger.info("Starting evaluation of {}", pl);
		for (int i = 0; i < repeats && !this.canceled && !Thread.currentThread().isInterrupted(); i++) {
			//logger.info("Evaluating {} with split #{}/{}", pl, i + 1, this.repeats);
			double score = basicEvaluator.getErrorRateForRandomSplit(pl, data, trainingPortion);
			//logger.info("Score for evaluation of {} with split #{}/{}: {}", pl, i + 1, this.repeats, score);
			stats.addValue(score);
		}
		if (Thread.currentThread().isInterrupted())
			throw new InterruptedException("MCCV has been interrupted");


		
		Double score = stats.getMean();
		//logger.info("Obtained score of {} for classifier {}.", score, pl);

		
		
		// write to database
		// write to variables
	}
	
}
