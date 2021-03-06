/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.pact.common.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import eu.stratosphere.pact.common.contract.FileDataSink;
import eu.stratosphere.pact.common.contract.FileDataSource;
import eu.stratosphere.pact.common.contract.GenericDataSink;
import eu.stratosphere.pact.common.contract.GenericDataSource;
import eu.stratosphere.pact.common.util.Visitable;
import eu.stratosphere.pact.common.util.Visitor;
import eu.stratosphere.pact.generic.contract.Contract;
import eu.stratosphere.pact.generic.contract.ContractUtil;
import eu.stratosphere.pact.generic.io.SequentialInputFormat;
import eu.stratosphere.pact.generic.io.SequentialOutputFormat;
import eu.stratosphere.util.dag.GraphModule;
import eu.stratosphere.util.dag.GraphPrinter;
import eu.stratosphere.util.dag.GraphTraverseListener;
import eu.stratosphere.util.dag.NodePrinter;
import eu.stratosphere.util.dag.OneTimeTraverser;

/**
 * The PactModule is a subgraph of a {@link PactProgram} with an arbitrary but
 * well-defined number of inputs and outputs. It is designed to facilitate
 * modularization and thus to increase the maintainability of large
 * PactPrograms. While the interface of the module are the number of inputs and
 * outputs, the actual implementation consists of several interconnected {@link Contract}s that are connected to the
 * inputs and outputs of the
 * PactModule.
 */
public class PactModule extends GraphModule<Contract, GenericDataSource<?>, GenericDataSink> implements
		Visitable<Contract> {
	/**
	 * Initializes a PactModule having the given name, number of inputs, and
	 * number of outputs.
	 * 
	 * @param numberOfInputs
	 *        the number of inputs
	 * @param numberOfOutputs
	 *        the number of outputs.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public PactModule(final int numberOfInputs, final int numberOfOutputs) {
		super(numberOfInputs, numberOfOutputs, ContractNavigator.INSTANCE);
		for (int index = 0; index < numberOfInputs; index++)
			this.setInput(index,
				new FileDataSource((Class) SequentialInputFormat.class, String.format("file:///%d", index),
					"Source " + index));
		for (int index = 0; index < numberOfOutputs; index++)
			this.setOutput(index, new FileDataSink(SequentialOutputFormat.class, String.format("file:///%d", index),
				"Sink " + index));
	}

	/**
	 * Traverses the pact plan, starting from the data outputs that were added
	 * to this program.
	 * 
	 * @see eu.stratosphere.pact.common.plan.Visitable#accept(eu.stratosphere.pact.common.plan.Visitor)
	 */
	@Override
	public void accept(final Visitor<Contract> visitor) {
		final OneTimeVisitor<Contract> oneTimeVisitor = new OneTimeVisitor<Contract>(visitor);
		for (final Contract output : this.getAllOutputs())
			output.accept(oneTimeVisitor);
	}

	@Override
	public String toString() {
		final GraphPrinter<Contract> dagPrinter = new GraphPrinter<Contract>();
		dagPrinter.setNodePrinter(new NodePrinter<Contract>() {
			@Override
			public String toString(final Contract node) {
				final int inputIndex = PactModule.this.inputNodes.indexOf(node);
				if (inputIndex != -1)
					return String.format("Input %d", inputIndex);
				final int outputIndex = PactModule.this.outputNodes.indexOf(node);
				if (outputIndex != -1)
					return String.format("Output %d", outputIndex);
				return String.format("%s [%s]", node.getClass().getSimpleName(), node.getName());
			}
		});
		dagPrinter.setWidth(40);
		return dagPrinter.toString(this.getAllOutputs(), ContractNavigator.INSTANCE);
	}

	/**
	 * Wraps the graph given by the sinks and referenced contracts in a
	 * PactModule.
	 * 
	 * @param name
	 *        the name of the PactModule
	 * @param sinks
	 *        all sinks that span the graph to wrap
	 * @return a PactModule representing the given graph
	 */
	public static PactModule valueOf(final Collection<? extends Contract> sinks) {
		final List<Contract> inputs = new ArrayList<Contract>();

		OneTimeTraverser.INSTANCE.traverse(sinks, ContractNavigator.INSTANCE,
			new GraphTraverseListener<Contract>() {
				@Override
				public void nodeTraversed(final Contract node) {
					final List<List<Contract>> contractInputs = ContractUtil.getInputs(node);
					if (contractInputs.size() == 0)
						inputs.add(node);
					else
						for (final List<Contract> input : contractInputs)
							if (input.size() == 0)
								inputs.add(node);
				};
			});

		final PactModule module = new PactModule(inputs.size(), sinks.size());
		int sinkIndex = 0;
		for (final Contract sink : sinks) {
			if (sink instanceof GenericDataSink)
				module.setOutput(sinkIndex, (GenericDataSink) sink);
			else
				module.getOutput(sinkIndex).addInput(sink);
			sinkIndex++;
		}

		for (int index = 0; index < inputs.size();) {
			final Contract node = inputs.get(index);
			final List<List<Contract>> contractInputs = ContractUtil.getInputs(node);
			if (contractInputs.isEmpty())
				module.setInput(index++, (GenericDataSource<?>) node);
			else {
				for (int unconnectedIndex = 0; unconnectedIndex < contractInputs.size(); unconnectedIndex++)
					if (contractInputs.get(unconnectedIndex).isEmpty())
						contractInputs.get(unconnectedIndex).add(module.getInput(index++));
				ContractUtil.setInputs(node, contractInputs);
			}
		}
		return module;
	}

	/**
	 * Wraps the graph given by the sinks and referenced contracts in a
	 * PactModule.
	 * 
	 * @param name
	 *        the name of the PactModule
	 * @param sinks
	 *        all sinks that span the graph to wrap
	 * @return a PactModule representing the given graph
	 */
	public static PactModule valueOf(final Contract... sinks) {
		return valueOf(Arrays.asList(sinks));
	}

}
