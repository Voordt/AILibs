package defaultEval.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.model.Parameter;

/**
 * Does not return any arguments. When pipeline is created default values will be used.
 * 
 * @author Joshua
 *
 */
public class DefaultOptimizer extends Optimizer{

	
	public DefaultOptimizer(Component searcher, Component evaluator, Component classifier, String dataSet, File environment, File dataSetFolder, int seed, int maxRuntimeParam, int maxRuntime) {
		super(searcher, evaluator, classifier, dataSet, environment, dataSetFolder, seed, maxRuntimeParam, maxRuntime);
	}
	
	@Override
	public void optimize() {
		
		Map<String, String> searcherParameter = new HashMap<>();
		finalSearcher = new ComponentInstance(searcher, searcherParameter, new HashMap<>());
		
		Map<String, String> evaluatorParameter = new HashMap<>();
		finalEvaluator = new ComponentInstance(evaluator, evaluatorParameter, new HashMap<>());
		
		Map<String, String> classifierParameter = new HashMap<>();
		finalClassifier = new ComponentInstance(classifier, classifierParameter, new HashMap<>());
	}
	
	
	
	
}
