package training;

import jNeatCommon.IOseq;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.StringTokenizer;

import training.evaluators.MySupervisedEvaluator;

import jneat.Neat;
import jneat.evolution.Organism;
import jneat.evolution.Population;
import jneat.evolution.Species;
import jneat.neuralNetwork.Genome;

public class Trainer {
	String parameterFileName;
	String debugParameterFileName;
	String genomeFileName;
	String genomeBackupFileName;
	String lastPopulationInfoFileName;
	String generationInfoFolder;
	String winnerFolder;
	String nameOfExperiment;
	int numberOfGenerations;
	boolean stopOnFirstGoodOrganism;
	MySupervisedEvaluator evaluator;
	
	public Trainer(String parameterFileName, String debugParameterFileName, String genomeFileName, 
			String genomeBackupFileName, String lastPopulationInfoFileName, String generationInfoFolder, 
			String winnerFolder, String nameOfExperiment, int numberOfGenerations, 
			boolean stopOnFirstGoodOrganism, MySupervisedEvaluator evaluator){
		
		this.parameterFileName=parameterFileName;
		this.debugParameterFileName=debugParameterFileName;
		this.genomeFileName=genomeFileName;
		this.genomeBackupFileName=genomeBackupFileName;
		this.lastPopulationInfoFileName=lastPopulationInfoFileName;
		this.generationInfoFolder = generationInfoFolder;
		this.winnerFolder = winnerFolder;
		this.nameOfExperiment=nameOfExperiment;
		this.numberOfGenerations=numberOfGenerations;
		this.stopOnFirstGoodOrganism = stopOnFirstGoodOrganism;
		this.evaluator = evaluator;				
	}
	
	public Trainer(){
		
	}
	
	public boolean trainNetwork(){
		boolean status;
		
		//Test if all variables have been set
		if (!testVariables()){
			System.out.println("Not all string variables set. Training will not commence");
			return false;
		}
		
		//Initialise the neat class
		Neat.initbase();
		
		//Import the parameters to be used by NEAT
		status = importParameters(parameterFileName);
		if (!status){
			return false;
		}
		
		//Save imported parameters to new file
		//Can be used when debugging		
		writeParametersToFile(debugParameterFileName);
		
		//Run experiments
		System.out.println("Start experiment " + nameOfExperiment);
		status = experimentSession(genomeFileName, numberOfGenerations, stopOnFirstGoodOrganism);
		
		return status;
	}
	
	private boolean testVariables(){
		 if (!testSingleVariable(parameterFileName)) return false;
		 if (!testSingleVariable(debugParameterFileName)) return false;
		 if (!testSingleVariable(genomeFileName)) return false;
		 if (!testSingleVariable(genomeBackupFileName)) return false;
		 if (!testSingleVariable(lastPopulationInfoFileName)) return false;
		 if (!testSingleVariable(generationInfoFolder)) return false;
		 if (!testSingleVariable(winnerFolder)) return false;
		 if (!testSingleVariable(nameOfExperiment)) return false;
		 if (numberOfGenerations <= 0) return false;
		 if (evaluator == null) return false;
		
		return true;
	}
	
	private boolean testSingleVariable(String s){
		boolean status = !s.contentEquals("");
		return status;
	}
	
	private boolean importParameters (String parameterFileName){
		boolean status = Neat.readParam(parameterFileName);
		if (status){
			System.out.println("Parameter read okay");
		} else{
			System.out.println("Error in parameter read");
		}
		
		return status;
	}
	
	private void writeParametersToFile(String parameterFileName){
		Neat.writeParam(parameterFileName);
	}
	
	/**
	 * Starts the experiment
	 * @param starterGenomeFileName
	 * @param generations
	 */
	private boolean experimentSession (String starterGenomeFileName, int generations, boolean stopOnFirstGoodOrganism){
		
		//Open the file with the starter genome data
		IOseq starterGenomeFile = new IOseq(starterGenomeFileName);
		boolean ret = starterGenomeFile.IOseqOpenR();
		
		if (ret){
			//Create starter genome
			Genome starterGenome = createGenome(starterGenomeFile);
			
			//Start experiments
			for (int expCount = 0; expCount < Neat.p_num_runs; expCount++){
				runExperiment(starterGenome, generations, stopOnFirstGoodOrganism);
			}
			
		} else{
			System.out.println("Error during opening of " + starterGenomeFileName);
			starterGenomeFile.IOseqCloseR();
			return false;
		}
		
		starterGenomeFile.IOseqCloseR();
		return true;
	}
	/**
	 * Reads a file and creates a genome based on the data in that file
	 * @param starterGenomeFile
	 * @return
	 */
	private Genome createGenome (IOseq starterGenomeFile){
		String curWord;
		
		System.out.println("Read starter genome");
		
		//Read file
		String line = starterGenomeFile.IOseqRead();
		StringTokenizer st = new StringTokenizer(line);
		
		//Skip first word in file
		curWord = st.nextToken();
		
		//Read ID of the genome
		curWord = st.nextToken();
		int id = Integer.parseInt(curWord);
		
		//Create the genome
		System.out.println("Create genome id " + id);
		Genome startGenome = new Genome (id,starterGenomeFile);
		
		//Backup initial genome
		//Probably used for debugging
		startGenome.print_to_filename(genomeBackupFileName);
				
		return startGenome;
		
	}
	/**
	 * Runs an experiment where populations are evolved from a basic genome
	 * @param starterGenome
	 * @param generations
	 */
	private void runExperiment(Genome starterGenome, int generations, boolean stopOnFirstGoodOrganism){
		String mask6 = "000000";
		DecimalFormat fmt6 = new DecimalFormat(mask6);
		
		//Create population
		System.out.println("Spawning population from starter genome");
		Population pop = new Population(starterGenome, Neat.p_pop_size);
		
		//Verify population
		System.out.println("Verifying spawned population");
		pop.verify();
		
		//Run experiment
		System.out.println("Starting evolution");
		for (int gen = 1; gen <= generations; gen++){
			System.out.print("\n---------------- E P O C H  < " + gen+" >--------------");
			
			String filenameEpochInfo = "g_" + fmt6.format(gen);
			boolean status = goThroughEpoch(pop, gen, filenameEpochInfo);
			
			if (stopOnFirstGoodOrganism){
				//Break out if a good enough organism has been found
				if (status){
					break;
				}
			}
		}
		
		//Prints information about the last generation 
		System.out.print("\n  Population : innov num   = " + pop.getCur_innov_num()); //Prints the current number of innovations
		System.out.print("\n             : cur_node_id = " + pop.getCur_node_id());  //Current number of nodes (??)
		
		//Writes population info to file for the last population 
		pop.print_to_filename(lastPopulationInfoFileName);
	}
	
	/**
	 * Evolves a new generation for the population
	 * @param pop
	 * @param generation
	 * @param filenameEpochInfo
	 * @return True if a winner has been found in the population. False otherwise
	 */
	private boolean goThroughEpoch(Population pop, int generation, String filenameEpochInfo){
		boolean status = false;
		
		//Evaluate each organism to see if it is a winner
		boolean win = false;
		
		Iterator itr_organism;
		itr_organism = pop.organisms.iterator();
		
		while (itr_organism.hasNext()){
			//point to organism
			Organism curOrganism = ((Organism) itr_organism.next());
			//evaluate 
			status = evaluator.evaluate(curOrganism);
			// if is a winner , store a flag
			if (status){
				win = true;
			}
		 }
		
		//compute average and max fitness for each species
		Iterator itr_specie;
		itr_specie = pop.species.iterator();
		while (itr_specie.hasNext()) {
			Species curSpecie = ((Species) itr_specie.next());
			curSpecie.compute_average_fitness();
			curSpecie.compute_max_fitness();
		}
		 
		// Only print to file every print_every generations
		if (win || (generation % Neat.p_print_every) == 0){
			pop.print_to_file_by_species(generationInfoFolder + "\\" + filenameEpochInfo);
		}
		  
		// if a winner exist, write to file	   
		if (win) {
			int cnt = 0;
			itr_organism = pop.getOrganisms().iterator();
			while (itr_organism.hasNext()) {
				Organism _organism = ((Organism) itr_organism.next());
				if (_organism.winner){
					System.out.print("\n   -WINNER IS #" + _organism.genome.getGenome_id());
					_organism.getGenome().print_to_filename(winnerFolder +  "\\" + nameOfExperiment + "_win " + cnt);
					cnt++;
				}
			}
		}
		
		// wait an epoch and make a reproduction of the best species
		pop.epoch(generation);
		if (win){
			System.out.print("\t\t** I HAVE FOUND A CHAMPION **");
			return true;
		} else { 
			return false;
		}		
	}
	
	public void setParameterFileName(String parameterFileName) {
		this.parameterFileName = parameterFileName;
	}


	public void setDebugParameterFileName(String debugParameterFileName) {
		this.debugParameterFileName = debugParameterFileName;
	}


	public void setGenomeFileName(String genomeFileName) {
		this.genomeFileName = genomeFileName;
	}


	public void setGenomeBackupFileName(String genomeBackupFileName) {
		this.genomeBackupFileName = genomeBackupFileName;
	}


	public void setLastPopulationInfoFileName(String lastPopulationInfoFileName) {
		this.lastPopulationInfoFileName = lastPopulationInfoFileName;
	}


	public void setGenerationInfoFolder(String generationInfoFolder) {
		this.generationInfoFolder = generationInfoFolder;
	}


	public void setWinnerFolder(String winnerFolder) {
		this.winnerFolder = winnerFolder;
	}


	public void setNumberOfGenerations(int numberOfGenerations) {
		this.numberOfGenerations = numberOfGenerations;
	}


	public void setNameOfExperiment(String nameOfExperiment) {
		this.nameOfExperiment = nameOfExperiment;
	}


	public void setStopOnFirstGoodOrganism(boolean stopOnFirstGoodOrganism) {
		this.stopOnFirstGoodOrganism = stopOnFirstGoodOrganism;
	}
	
	public void setEvaluator(MySupervisedEvaluator evaluator) {
		this.evaluator = evaluator;
	}
}
