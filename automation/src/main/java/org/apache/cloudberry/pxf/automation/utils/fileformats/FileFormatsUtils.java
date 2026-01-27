package org.apache.cloudberry.pxf.automation.utils.fileformats;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import org.apache.cloudberry.pxf.automation.fileformats.IDataPreparer;
import org.apache.cloudberry.pxf.automation.structures.tables.basic.Table;

public class FileFormatsUtils {

	public static Object[] prepareData(IDataPreparer reader, int rows, Table dataTable) throws Exception {
		return reader.prepareData(rows, dataTable);
	}

	public static void prepareDataFile(Table dataTable, int amount, String pathToFile) throws Exception {
		File file = new File(pathToFile);
		file.delete();

		StringBuilder listString = new StringBuilder();

		for (List<String> row : dataTable.getData()) {
			for (String item : row) {
				listString.append(item).append(",");
			}
			listString.deleteCharAt(listString.length() - 1);
			listString.append(System.getProperty("line.separator"));
		}
		listString.deleteCharAt(listString.length() - 1);

		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));

		for (int i = 0; i < amount; i++) {
			out.println(listString);
		}
		out.close();
	}
}