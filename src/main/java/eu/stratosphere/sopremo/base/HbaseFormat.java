package eu.stratosphere.sopremo.base;

import java.io.IOException;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.pact.common.io.TableInputFormat;
import eu.stratosphere.pact.common.io.TableInputSplit;
import eu.stratosphere.pact.common.io.statistics.BaseStatistics;
import eu.stratosphere.sopremo.io.SopremoFormat;
import eu.stratosphere.sopremo.operator.Name;
import eu.stratosphere.sopremo.operator.Property;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.LongNode;
import eu.stratosphere.sopremo.type.MissingNode;
import eu.stratosphere.sopremo.type.ObjectNode;
import eu.stratosphere.sopremo.type.TextNode;

@Name(noun = "hbase")
public class HbaseFormat extends SopremoFormat {

	/**
	 * Initializes HbaseFormat.
	 */
	public HbaseFormat() {
	}

	private String tablename = null;

	public static class InputFormat extends AbstractSopremoInputFormat<TableInputSplit> {

		private TableInputFormat tableInputFormat;

		private String tablename;

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.io.SopremoFormat.AbstractSopremoInputFormat#configure(eu.stratosphere.nephele.
		 * configuration.Configuration)
		 */
		@Override
		public void configure(Configuration parameters) {
			super.configure(parameters);
			parameters.setString(TableInputFormat.INPUT_TABLE, this.tablename);
			parameters.setString(TableInputFormat.CONFIG_LOCATION, "/etc/hbase/conf/hbase-site.xml");
			this.tableInputFormat.configure(parameters);
		}
		
		/* (non-Javadoc)
		 * @see eu.stratosphere.pact.generic.io.InputFormat#createInputSplits(int)
		 */
		public TableInputSplit[] createInputSplits(int minNumSplits) throws IOException {
			return this.tableInputFormat.createInputSplits(minNumSplits);
		}
		
		/* (non-Javadoc)
		 * @see eu.stratosphere.pact.generic.io.InputFormat#getInputSplitType()
		 */
		public Class<? extends TableInputSplit> getInputSplitType() {
			return this.tableInputFormat.getInputSplitType();
		}
		
		

		/**
		 * @param cachedStatistics
		 * @return
		 * @see eu.stratosphere.pact.common.io.TableInputFormat#getStatistics(eu.stratosphere.pact.common.io.statistics.BaseStatistics)
		 */
		public BaseStatistics getStatistics(BaseStatistics cachedStatistics) {
			return this.tableInputFormat.getStatistics(cachedStatistics);
		}

		/**
		 * @return
		 * @throws IOException
		 * @see eu.stratosphere.pact.common.io.TableInputFormat#reachedEnd()
		 */
		@Override
		public boolean reachedEnd() throws IOException {
			return this.tableInputFormat.reachedEnd();
		}

		/**
		 * @throws IOException
		 * @see eu.stratosphere.pact.common.io.TableInputFormat#close()
		 */
		public void close() throws IOException {
			this.tableInputFormat.close();
		}

		/**
		 * @param split
		 * @throws IOException
		 * @see eu.stratosphere.pact.common.io.TableInputFormat#open(eu.stratosphere.pact.common.io.TableInputSplit)
		 */
		@Override
		public void open(TableInputSplit split) throws IOException {
			this.tableInputFormat.open(split);
		}

		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.io.SopremoFormat.SopremoInputFormat#nextValue()
		 */
		public IJsonNode nextValue() throws IOException {
			// create JSonNode with content
			// merge all family:column->value pairs in a result
			// TODO: handle mutliple versions
			// TODO: there are way too many object allocations in this code,
			// we should check whether that makes a difference, though
			ObjectNode value = new ObjectNode();

			Result res = this.tableInputFormat.getHbaseResult().getResult();

			KeyValue[] results = res.raw();
			value.put("row", new TextNode(new String(results[0].getRow())));
			value.put("timestamp", new LongNode(results[0].getTimestamp()));
			for (KeyValue kv : results) {
				String familyString = new String(kv.getFamily());
				// check of current column family is already in node
				IJsonNode family = value.get(familyString);
				if (family == MissingNode.getInstance()) {
					family = new ObjectNode();
					value.put(familyString, family);
				}
				// insert qualifier and value
				((ObjectNode) family).put(new String(kv.getQualifier()),
					new TextNode(new String(kv.getValue())));
			}
			return null;
		}
	}

	@Property(preferred = true)
	@Name(noun = "table")
	public void setURLParameter(String tablename) {
		if (tablename == null) {
			throw new NullPointerException("value expression must not be null");
		}
		this.tablename = tablename;
	}

	/**
	 * Returns the tablename.
	 * 
	 * @return the tablename
	 */
	public String getURLParameter() {
		return this.tablename;
	}

}
