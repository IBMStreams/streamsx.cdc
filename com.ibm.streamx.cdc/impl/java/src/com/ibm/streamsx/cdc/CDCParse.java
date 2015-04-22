package com.ibm.streamsx.cdc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ibm.replication.cdc.scripting.EmbeddedScript;
import com.ibm.replication.cdc.scripting.EmbeddedScriptException;
import com.ibm.replication.cdc.scripting.Result;
import com.ibm.replication.cdc.scripting.ResultStringTable;
import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.log4j.TraceLevel;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.PrimitiveOperator;

/**
 * Processes tuples which have the following fixed format:
 * <ul>
 * <li>rstring txTableName</li>
 * <li>rstring txTimestamp</li>
 * <li>rstring txId</li>
 * <li>rstring txEntryType</li>
 * <li>rstring txUser</li>
 * <li>rstring data</li>
 * </ul>
 * <p>
 * The table name by which the tuple is identified must match the
 * qualifiedTableName defined for the CDCParse operator. If tuples from other
 * tables are received, a warning message is issued and the tuple is discarded.
 * </p>
 * <p>
 * There is a special tuple for which the txTableName starts with a "*".
 * </p>
 * <ul>
 * <li>***INITIALIZE*** : Indicates that the subscription has been (re-)started
 * and that tuples will be sent. When received, the CDCParse operator reads the
 * CDC configuration for the parsed table as the table definition may have
 * changed.</li>
 * <li>
 * </ul>
 * <p>
 * There is no special action that happens on a punctuation (commit) at this
 * stage, but the punctuation is forwarded dowstream.
 * </p>
 */
@PrimitiveOperator(name = "CDCParse", namespace = "com.ibm.streamsx.cdc", description = "Operator which parses the incoming raw tuples and converts them into the output tuple specific for the table configured in the operator.")
@InputPorts({ @InputPortSet(description = "Port that ingests tuples", cardinality = 1, optional = false, windowingMode = WindowMode.NonWindowed, windowPunctuationInputMode = WindowPunctuationInputMode.Oblivious) })
@OutputPorts({ @OutputPortSet(description = "Port that produces tuples", cardinality = 1, optional = false, windowPunctuationOutputMode = WindowPunctuationOutputMode.Generating) })
@Icons(location16 = "icons/CDCParse_16x16.png", location32 = "icons/CDCParse_32x32.png")
public class CDCParse extends AbstractOperator {

	private static Logger LOGGER = Logger.getLogger(CDCParse.class);

	protected OperatorContext operatorContext;
	/**
	 * Parameter separator. This parameter depicts the character used to
	 * separate the columns in the data field.
	 */
	protected String separator = "\\|";

	@Parameter(description = "Separator to be used to split the fields in the data field received.", name = "separator", optional = true)
	public void setSeparator(String separator) {
		this.separator = separator;
	}

	public String getSeparator() {
		return separator;
	}

	/**
	 * Parameter cdcExportXml. This parameter identifies the XML file that holds
	 * the exported CDC subscription. You can use an exported subscription if
	 * the Streams server cannot connect to the Access Server to retrieve
	 * subscription information.
	 * 
	 * This parameter is mutually exclusive with the
	 * accessServerConnectionDocument parameter.
	 */
	protected String cdcExportXml = "";

	@Parameter(description = "XML file of exported CDC subscription", name = "cdcExportXml", optional = true)
	public void setCdcExportXml(String cdcExportXml) {
		this.cdcExportXml = cdcExportXml;
	}

	public String getCdcExportXml() {
		return cdcExportXml;
	}

	/**
	 * Parameter accessServerConnectionDocument. This optional parameter
	 * specifies the path name of the file that contains the connection
	 * properties of the InfoSphere Data Replication CDC Access Server. When the
	 * CDCParse operator is invoked with an "init" operation, is uses the
	 * information in this XML document to retrieve the specified subscription
	 * and table mapping.
	 * 
	 * The parameter is mutually exclusive with the cdcExportXml parameter.
	 */

	protected String accessServerConnectionDocument = "";

	@Parameter(description = "XML document with connection information to Access Server", name = "accessServerConnectionDocument", optional = true)
	public void setAccessServerConnectionDocument(
			String accessServerConnectionDocument) {
		this.accessServerConnectionDocument = accessServerConnectionDocument;
	}

	public String getAccessServerConnectionDocument() {
		return accessServerConnectionDocument;
	}

	/**
	 * Parameter dataStore. This optional parameter specifies the name of the
	 * CDC source data store that defines the subscription that maps the table
	 * in question. The parameter is mandatory if the
	 * accessServerConnectionDocument is specified, otherwise it is ignored.
	 */
	protected String dataStore = "";

	@Parameter(description = "Name of the CDC data store", name = "dataStore", optional = true)
	public void setDataStore(String dataStore) {
		this.dataStore = dataStore;
	}

	public String getDataStore() {
		return dataStore;
	}

	/**
	 * Parameter subscription. This optional parameter specifies the name of the
	 * CDC subscription that maps the table in question. The parameter is
	 * mandatory if the accessServerConnectionDocument is specified, otherwise
	 * it is ignored.
	 */
	protected String subscription = "";

	@Parameter(description = "Name of the subscription", name = "subscription", optional = true)
	public void setSubscription(String subscription) {
		this.subscription = subscription;
	}

	public String getSubscription() {
		return subscription;
	}

	/**
	 * Parameter qualifiedTableName. The parameter is converted to uppercase
	 * during initialization as the entries coming from CDC will have uppercase
	 * table names.
	 */
	protected String qualifiedTableName = "";

	@Parameter(description = "Table to be parsed: schema.tablename", name = "qualifiedTableName", optional = false)
	public void setQualifiedTableName(String qualifiedTableName) {
		this.qualifiedTableName = qualifiedTableName.toUpperCase();
	}

	public String getQualifiedTableName() {
		return qualifiedTableName;
	}

	/**
	 * Parameter beforeImagePrefix. Defines the prefix for the tuple fields
	 * identifying the before-image of the table columns. Default is "b_"
	 */
	protected String beforeImagePrefix = "b_";

	@Parameter(description = "Prefix of the fields representing the before image columns", name = "beforeImagePrefix", optional = true)
	public void setBeforeImagePrefix(String beforeImagePrefix) {
		this.beforeImagePrefix = beforeImagePrefix;
	}

	public String getBeforeImagePrefix() {
		return beforeImagePrefix;
	}

	/**
	 * Parameter afterImagePrefix. Defines the prefix for the tuple fields
	 * identifying the after image of the table columns. Default is ""
	 */
	protected String afterImagePrefix = "";

	@Parameter(description = "Prefix of the fields representing the after image columns", name = "afterImagePrefix", optional = true)
	public void setAfterImagePrefix(String afterImagePrefix) {
		this.afterImagePrefix = afterImagePrefix;
	}

	public String getAfterImagePrefix() {
		return afterImagePrefix;
	}

	/**
	 * Parameter fillDeleteAfterImage. This parameter causes CDC to populate the
	 * after-image fields in case a delete operation is received. By setting
	 * this parameter to true, you can use the after-image columns with every
	 * received tuple, regardless of its operation.
	 */
	protected boolean fillDeleteAfterImage = true;

	@Parameter(description = "Populate after-image in case of a delete operation. By setting this parameter to true, you can use the after-image columns with every received tuple, regardless of its operation.", name = "fillDeleteAfterImage", optional = true)
	public void setFillDeleteAfterImage(boolean fillDeleteAfterImage) {
		this.fillDeleteAfterImage = fillDeleteAfterImage;
	}

	public boolean getFillDeleteAfterImage() {
		return fillDeleteAfterImage;
	}

	protected HashMap<String, Integer> outputData = new HashMap<String, Integer>();
	protected HashMap<String, Integer> outputDeletedRecord = new HashMap<String, Integer>();

	/**
	 * Initialize this operator. Called once before any tuples are processed.
	 * 
	 * @param operatorContext
	 *            OperatorContext for this operator.
	 * @throws Exception
	 *             Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void initialize(OperatorContext operatorContext)
			throws Exception {
		// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(operatorContext);
		this.operatorContext = operatorContext;
		LOGGER.log(TraceLevel.TRACE, "Operator " + operatorContext.getName()
				+ " initializing in PE: " + operatorContext.getPE().getPEId()
				+ " in Job: " + operatorContext.getPE().getJobId());
		// Check that not both accessServerConnectionDocument and cdcExportXml
		// parameters have been specified.
		if (!getAccessServerConnectionDocument().isEmpty()
				&& !getCdcExportXml().isEmpty())
			throw new Exception(
					"Parameters accessServerConnectionDocument and cdcExportXml"
							+ " must not both be specified in the CDCParse operator.");
		// Check that dataStore is specified if
		// accessServerConnectionDocument is specified.
		if (!getAccessServerConnectionDocument().isEmpty()
				&& dataStore.isEmpty())
			throw new Exception(
					"DataStore must be specified if the mapped columns are to be"
							+ " retrieved through CHCCLP.");
		// Check that subscription is specified if
		// accessServerConnectionDocument is specified.
		if (!getAccessServerConnectionDocument().isEmpty()
				&& subscription.isEmpty())
			throw new Exception(
					"Subscription must be specified if the mapped columns are to be"
							+ " retrieved through CHCCLP.");
		// Now, map the table columns to the tuple fields
		mapColumnsToTuple();
		LOGGER.log(TraceLevel.TRACE,
				"CDCParse operator initialized, ready to receive tuples");
	}

	/**
	 * Notification that initialization is complete and all input and output
	 * ports are connected and ready to receive and submit tuples.
	 * 
	 * @throws Exception
	 *             Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void allPortsReady() throws Exception {
		// This method is commonly used by source operators.
		// Operators that process incoming tuples generally do not need this
		// notification.
		OperatorContext context = getOperatorContext();
		LOGGER.log(TraceLevel.TRACE, "Operator " + context.getName()
				+ " all ports are ready in PE: " + context.getPE().getPEId()
				+ " in Job: " + context.getPE().getJobId());
	}

	/**
	 * Process an incoming tuple that arrived on the specified port.
	 * <P>
	 * Copy the incoming tuple to a new output tuple and submit to the output
	 * port.
	 * </P>
	 * 
	 * @param inputStream
	 *            Port the tuple is arriving on.
	 * @param tuple
	 *            Object representing the incoming tuple.
	 * @throws Exception
	 *             Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public final void process(StreamingInput<Tuple> inputStream, Tuple tuple)
			throws Exception {

		// Create a new tuple for output port 0
		StreamingOutput<OutputTuple> outStream = getOutput(0);
		OutputTuple outTuple = outStream.newTuple();
		LOGGER.log(TraceLevel.TRACE, "Processing " + tuple);

		// Check that the tuple table matches the configured table name.
		String txTableName = tuple.getString("txTableName");
		if (txTableName.startsWith("*")) {
			// If the subscription has been restarted, update the
			// column-to-tuple mapping
			if (txTableName.equals("***INITIALIZE***")) {
				mapColumnsToTuple();
			}
		} else if (txTableName.equals(qualifiedTableName)) {
			// Copy across all matching attributes, including the empty ones
			outTuple.assign(tuple);
			String[] data = tuple.getString("data").split(separator, -1);
			for (String k : outputData.keySet()) {
				outTuple.setString(k, data[outputData.get(k)]);
			}
			// If the before image of the delete record must populate the
			// regular fields, do so
			if (fillDeleteAfterImage) {
				if (tuple.getString("txEntryType").equals("D")) {
					for (String k : outputDeletedRecord.keySet()) {
						outTuple.setString(k, data[outputDeletedRecord.get(k)]);
					}
				}
			}
			// Submit new tuple to output port 0
			outStream.submit(outTuple);
		} else {
			LOGGER.log(TraceLevel.ERROR, "Invalid table name " + txTableName
					+ " received by CDCParse operator. Expected table "
					+ qualifiedTableName);
		}
	}

	/**
	 * Process an incoming punctuation that arrived on the specified port.
	 * 
	 * @param stream
	 *            Port the punctuation is arriving on.
	 * @param mark
	 *            The punctuation mark
	 * @throws Exception
	 *             Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public void processPunctuation(StreamingInput<Tuple> stream,
			Punctuation mark) throws Exception {
		// For window markers, punctuate all output ports
		super.processPunctuation(stream, mark);
	}

	/**
	 * Shutdown this operator.
	 * 
	 * @throws Exception
	 *             Operator failure, will cause the enclosing PE to terminate.
	 */
	public synchronized void shutdown() throws Exception {
		OperatorContext context = getOperatorContext();
		LOGGER.log(TraceLevel.TRACE, "Operator " + context.getName()
				+ " shutting down in PE: " + context.getPE().getPEId()
				+ " in Job: " + context.getPE().getJobId());
		// Must call super.shutdown()
		super.shutdown();
	}

	private void mapColumnsToTuple() throws Exception {
		StreamSchema outputSchema = operatorContext.getStreamingOutputs()
				.get(0).getStreamSchema();
		String outputTuple = "<";
		for (String attrName : outputSchema.getAttributeNames()) {
			if (!outputTuple.equals("<"))
				outputTuple += ", ";
			outputTuple += outputSchema.getAttribute(attrName).getType()
					.getLanguageType();
			outputTuple += " " + attrName;
		}
		outputTuple += ">";
		LOGGER.log(TraceLevel.TRACE, "Output tuple for CDCParse operator is "
				+ outputTuple);
		ArrayList<String> selectedColumns;
		if (!getCdcExportXml().isEmpty())
			selectedColumns = getColumnsFromExportXml();
		else
			selectedColumns = getColumnsFromChcclp();

		// Create a mapping of the table columns to the output port attributes,
		// this
		// to make the mapping of the actual data most efficient
		int p = 0;
		// First map the before image columns
		for (int i = 0; i < selectedColumns.size(); i++) {
			Attribute attr = outputSchema.getAttribute(beforeImagePrefix
					+ selectedColumns.get(i));
			if (attr != null) {
				LOGGER.log(
						TraceLevel.TRACE,
						"Table column "
								+ selectedColumns.get(i)
								+ " (before image) is mapped to tuple attribute "
								+ attr.getName());
				outputData.put(attr.getName(), new Integer(p));
			}
			// Prepare column mapping for delete-image
			if (fillDeleteAfterImage) {
				attr = outputSchema.getAttribute(selectedColumns.get(i));
				if (attr != null) {
					LOGGER.log(TraceLevel.TRACE, "Table column "
							+ selectedColumns.get(i)
							+ " (before image) is mapped to tuple attribute "
							+ attr.getName() + " for the delete image");
					outputDeletedRecord.put(attr.getName(), new Integer(p));
				}
			}
			p++;
		}
		// Then map the after image columns
		for (int i = 0; i < selectedColumns.size(); i++) {
			Attribute attr = outputSchema.getAttribute(afterImagePrefix
					+ selectedColumns.get(i));
			if (attr != null) {
				LOGGER.log(
						TraceLevel.TRACE,
						"Table column "
								+ selectedColumns.get(i)
								+ " (after image) is mapped to tuple attribute "
								+ attr.getName());
				outputData.put(attr.getName(), new Integer(p));
			}
			p++;
		}

		// Check that all fields in the output port have been mapped
		for (String attrName : outputSchema.getAttributeNames()) {
			if (!attrName.startsWith("tx")) {
				String checkColumn = null;
				if (attrName.startsWith("b_"))
					checkColumn = attrName.substring(2);
				else
					checkColumn = attrName;
				if (outputData.get(checkColumn) == null) {
					LOGGER.log(
							TraceLevel.WARN,
							"Output tuple attribute "
									+ attrName
									+ " is not mapped to any of the replicated columns, values will be empty.");
				}
			}
		}
	}

	private ArrayList<String> getColumnsFromChcclp() throws Exception {
		ArrayList<String> selectedColumns = new ArrayList<String>();
		// Now parse the Access Server Connection document
		LOGGER.log(TraceLevel.TRACE, "Parsing connection document "
				+ accessServerConnectionDocument);
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		Document document = dbf.newDocumentBuilder().parse(
				new File(accessServerConnectionDocument));
		Element rootElement = Utility.getRootElement(document);
		String accessServerHost = Utility.getChildElementValue(rootElement,
				"Host");
		String accessServerPort = Utility.getChildElementValue(rootElement,
				"Port");
		String accessServerUser = Utility.getChildElementValue(rootElement,
				"User");
		String accessServerPassword = Utility.getChildElementValue(rootElement,
				"Password");
		LOGGER.log(TraceLevel.TRACE,
				"Found the following properties in the connection document: "
						+ "Host=" + accessServerHost + ", Port="
						+ accessServerPort + ", User=" + accessServerUser
						+ ", Password=" + accessServerPassword);
		boolean tableFound = false;
		EmbeddedScript script = new EmbeddedScript();
		Result result;
		try {
			script.open();
			scriptExecute(script, "connect server hostname " + accessServerHost
					+ " username " + accessServerUser + " password "
					+ accessServerPassword);
			scriptExecute(script, "connect datastore name " + getDataStore());
			scriptExecute(script, "select subscription name "
					+ getSubscription());
			scriptExecute(script, "list table mappings");
			result = script.getResult();
			if (result.getType() == Result.TABLE) {
				ResultStringTable table = (ResultStringTable) result;
				for (int i = 0; i < table.getRowCount(); i++) {
					String qualifiedTable = table.getValueAt(i, 0);
					// If the mapped source table matches the qualifiedTableName
					// parameter, retrieve the columns
					if (qualifiedTable
							.equalsIgnoreCase(getQualifiedTableName())) {
						tableFound = true;
						LOGGER.log(TraceLevel.TRACE, "Table " + qualifiedTable
								+ " found in the subscription");
						scriptExecute(
								script,
								"select table mapping sourceSchema "
										+ qualifiedTable.split("[.]")[0]
										+ " sourceTable "
										+ qualifiedTable.split("[.]")[1]);
						scriptExecute(script, "list source columns");
						result = script.getResult();
						ResultStringTable columns = (ResultStringTable) result;
						for (int c = 0; c < columns.getRowCount(); c++) {
							String columnName = columns.getValueAt(c, 0);
							String columnSelected = columns.getValueAt(c, 3);
							LOGGER.log(TraceLevel.TRACE, "Column found: "
									+ columnName + ", selected: "
									+ columnSelected);
							if (columnSelected.equalsIgnoreCase("Yes")) {
								selectedColumns.add(columnName);
							}
						}
					}
				}
			}
			script.close();
		} catch (Exception ese) {
			LOGGER.log(
					TraceLevel.ERROR,
					"Error while retrieving mapped columns through CHCCLP scripting. See previous messages for details.");
			throw new Exception(
					"Error while retrieving mapped columns through CHCCLP scripting. See previous messages for details.");
		}
		if (!tableFound)
			LOGGER.log(
					TraceLevel.WARN,
					"Table mapping for table "
							+ qualifiedTableName
							+ " not found in datastore, no tuples for this table will be processed");

		return selectedColumns;
	}

	private void scriptExecute(EmbeddedScript script, String chcclpCommand)
			throws Exception {
		LOGGER.log(TraceLevel.TRACE, "Executing CHCCLP command: "
				+ chcclpCommand);
		try {
			script.execute(chcclpCommand);
		} catch (EmbeddedScriptException e) {
			LOGGER.log(TraceLevel.ERROR, script.getResultCodeAndMessage());
			throw new Exception(script.getResultCodeAndMessage());
		}
	}

	/**
	 * Retrieves the selected columns from the selected table into an ArrayList.
	 */
	private ArrayList<String> getColumnsFromExportXml() throws SAXException,
			IOException, ParserConfigurationException {
		ArrayList<String> selectedColumns = new ArrayList<String>();
		// Now parse the CDC Export XML document
		LOGGER.log(TraceLevel.TRACE,
				"Parsing subscription export XML document " + cdcExportXml);
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		Document document = dbf.newDocumentBuilder().parse(
				new File(cdcExportXml));
		NodeList tableMappings = Utility.getXmlTags(document, "TableMapping");
		boolean tableFound = false;
		for (int t = 0; t < tableMappings.getLength(); t++) {
			Node tableMapping = tableMappings.item(t);
			String tableName = Utility.getXMLValue(tableMapping, "sourceUser")
					+ "."
					+ Utility.getXMLValue(tableMapping, "sourceTableName");
			if (tableName.equalsIgnoreCase(qualifiedTableName)) {
				tableFound = true;
				LOGGER.log(TraceLevel.TRACE, "Found table mapping for table "
						+ tableName);
				NodeList columnMappings = Utility.getXmlTags(tableMapping,
						"SourceColumn");
				for (int c = 0; c < columnMappings.getLength(); c++) {
					Node columnMapping = columnMappings.item(c);
					String columnName = Utility.getXMLValue(columnMapping,
							"columnName");
					boolean columnSelected = Utility.getXMLValueAsBoolean(
							columnMapping, "selected");
					LOGGER.log(TraceLevel.TRACE, "Column found: " + columnName
							+ ", selected: " + columnSelected);
					if (columnSelected) {
						selectedColumns.add(columnName);
					}
				}
			}
		}
		if (!tableFound)
			LOGGER.log(
					TraceLevel.WARN,
					"Table mapping for table "
							+ qualifiedTableName
							+ " not found in XML file, no tuples for this table will be processed");
		return selectedColumns;
	}

}
