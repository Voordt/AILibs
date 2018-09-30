package defaultEval.core;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import de.upb.crc901.automl.hascocombinedml.MLServicePipelineFactory;
import de.upb.crc901.automl.pipeline.service.MLServicePipeline;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.serialization.ComponentLoader;
import scala.util.parsing.combinator.testing.Str;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class Util {
	
	/** Suffix Types for unique params */
	enum ParamType {
		searcher, evaluator, classifier
	}

	/**
	 * Loads Classifiers from the environment
	 * 
	 * @param cl Component loader to be used
	 * @param envPath Path to the environment
	 * @throws IOException
	 */
	public static void loadClassifierComponents(ComponentLoader cl, String envPath) throws IOException {
		cl.loadComponents(new File(envPath + "/models/weka-classifiers.json"));
	}

	/**
	 * Loads Preprocessors from the environment, Note that this is a modified version which does not include the Attribute Selection Component, only Searchers and Evaluators
	 * 
	 * @param cl Component loader to be used
	 * @param envPath Path to the environment
	 * @throws IOException
	 */
	public static void loadPreprocessorComponents(ComponentLoader cl, String envPath) throws IOException {
		cl.loadComponents(new File(envPath + "/models/weka-preprocessors.json"));
	}

	/**
	 * Creates a Pipeline Instance 
	 * 
	 * @param searcher 
	 * @param searcherParameter
	 * @param evaluator
	 * @param evaluatorParameter
	 * @param classifier
	 * @param classifierParameter
	 * @return
	 */
	public static ComponentInstance createPipeline(Component searcher, Map<String, String> searcherParameter,
			Component evaluator, Map<String, String> evaluatorParameter, Component classifier,
			Map<String, String> classifierParameter) {

		if (searcher != null) {
			ComponentInstance searcherInstance = new ComponentInstance(searcher, searcherParameter, new HashMap<>());
			ComponentInstance evaluatorInstance = new ComponentInstance(evaluator, evaluatorParameter, new HashMap<>());
			ComponentInstance classifierInstance = new ComponentInstance(classifier, classifierParameter,
					new HashMap<>()); 

			Map<String, ComponentInstance> satisfactionOfRequiredInterfacesPreprocessor = new HashMap<>();
			satisfactionOfRequiredInterfacesPreprocessor.put("eval", evaluatorInstance);
			satisfactionOfRequiredInterfacesPreprocessor.put("search", searcherInstance);

			Map<String, ComponentInstance> satisfactionOfRequiredInterfacesPipeline = new HashMap<>();
			satisfactionOfRequiredInterfacesPipeline.put("preprocessor", new ComponentInstance(
					new Component("preprocessor"), new HashMap<>(), satisfactionOfRequiredInterfacesPreprocessor));
			satisfactionOfRequiredInterfacesPipeline.put("classifier", classifierInstance);

			return new ComponentInstance(new Component("pipeline"), new HashMap<>(),
					satisfactionOfRequiredInterfacesPipeline);

		} else {
			return new ComponentInstance(classifier, classifierParameter, new HashMap<>());
		}

	}

	/**
	 * Creates a Pipeline Instance
	 * 
	 * @param searcherInstance
	 * @param evaluatorInstance
	 * @param classifierInstance
	 * @return
	 */
	public static ComponentInstance createPipeline(ComponentInstance searcherInstance,
			ComponentInstance evaluatorInstance, ComponentInstance classifierInstance) {
		if (searcherInstance != null) {
			Map<String, ComponentInstance> satisfactionOfRequiredInterfacesPreprocessor = new HashMap<>();
			satisfactionOfRequiredInterfacesPreprocessor.put("eval", evaluatorInstance);
			satisfactionOfRequiredInterfacesPreprocessor.put("search", searcherInstance);

			Map<String, ComponentInstance> satisfactionOfRequiredInterfacesPipeline = new HashMap<>();
			satisfactionOfRequiredInterfacesPipeline.put("preprocessor", new ComponentInstance(
					new Component("preprocessor"), new HashMap<>(), satisfactionOfRequiredInterfacesPreprocessor));
			satisfactionOfRequiredInterfacesPipeline.put("classifier", classifierInstance);

			return new ComponentInstance(new Component("pipeline"), new HashMap<>(),
					satisfactionOfRequiredInterfacesPipeline);

		} else {
			return classifierInstance;
		}
	}

	/**
	 * Loads a .arff Dataset, last Attribute as class
	 * 
	 * @param path Path to the Folder
	 * @param name Name of the set
	 * @return
	 */
	public static Instances loadInstances(String path, String name) {
		DataSource ds;
		Instances instances = null;
		try {
			ds = new DataSource(path + "/" + name + ".arff");
			instances = new Instances(ds.getDataSet());
			instances.setClassIndex(instances.numAttributes() - 1); // last one as class
		} catch (Exception e) {
			e.printStackTrace();
		}
		return instances;
	}

	/**
	 * creates a unique parameter name, to distinguish between Searcher, Evaluator and Classifier Parameters
	 * 
	 * @param name
	 * @param type
	 * @return
	 */
	public static String convertToUniqueParamName(String name, ParamType type) {
		switch (type) {
		case searcher:
			return String.format("%s_%s", name, "s");
		case evaluator:
			return String.format("%s_%s", name, "e");
		case classifier:
			return String.format("%s_%s", name, "c");

		default:
			return name;
		}
	}
	
	/**
	 * reverts a unique parameter name
	 * 
	 * @param name
	 * @param type
	 * @return
	 */
	public static String revertFromUniqueParamName(String name) {
		if(name.length() <= 2) {
			throw new IllegalArgumentException("Name is to short to be a unique name");
		}
		return name.substring(0, name.length()-2);
	}
	
	/**
	 * gets the Type of a unique parameter name (Searcher, Evaluator, Classifier)
	 * 
	 * @param name
	 * @param type
	 * @return
	 */
	public static ParamType getTypeFromUniqueParamName(String name) {
		if(name.endsWith("s")) {
			return ParamType.searcher;
		}else if (name.endsWith("e")) {
			return ParamType.evaluator;
		}else if (name.endsWith("c")) {
			return ParamType.classifier;
		}else {
			throw new IllegalArgumentException("Not a unique param Name: " + name);
		}
	}

}
