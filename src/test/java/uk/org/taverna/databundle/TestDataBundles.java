package uk.org.taverna.databundle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.purl.wf4ever.robundle.Bundle;

public class TestDataBundles {
	protected void checkSignature(Path zip) throws IOException {
		String MEDIATYPE = "application/vnd.wf4ever.robundle+zip";
		// Check position 30++ according to RO Bundle specification
		// http://purl.org/wf4ever/ro-bundle#ucf
		byte[] expected = ("mimetype" + MEDIATYPE + "PK").getBytes("ASCII");

		try (InputStream in = Files.newInputStream(zip)) {
			byte[] signature = new byte[expected.length];
			int MIME_OFFSET = 30;
			assertEquals(MIME_OFFSET, in.skip(MIME_OFFSET));
			assertEquals(expected.length, in.read(signature));
			assertArrayEquals(expected, signature);
		}
	}

	@Test
	public void close() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		assertTrue(Files.exists(dataBundle.getSource()));
		assertTrue(dataBundle.getRoot().getFileSystem().isOpen());
		DataBundles.getInputs(dataBundle);

		dataBundle.close();
		assertFalse(Files.exists(dataBundle.getSource()));
		assertFalse(dataBundle.getRoot().getFileSystem().isOpen());

	}

	@Test
	public void closeAndOpenDataBundle() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path zip = DataBundles.closeBundle(dataBundle);
		DataBundles.openBundle(zip);
	}

	@Test
	public void closeAndOpenDataBundleWithPortValue() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path port = DataBundles.getPort(inputs, "hello");
		DataBundles.setStringValue(port, "Hello");
		Path zip = DataBundles.closeBundle(dataBundle);

		Bundle newBundle = DataBundles.openBundle(zip);
		Path newInput = DataBundles.getInputs(newBundle);
		Path newPort = DataBundles.getPort(newInput, "hello");
		assertEquals("Hello", DataBundles.getStringValue(newPort));
	}

	@Test
	public void closeAndSaveDataBundle() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		DataBundles.getInputs(dataBundle);
		Path destination = Files.createTempFile("test", ".zip");
		Files.delete(destination);
		assertFalse(Files.exists(destination));
		DataBundles.closeAndSaveBundle(dataBundle, destination);
		assertTrue(Files.exists(destination));
	}

	@Test
	public void closeBundle() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path zip = DataBundles.closeBundle(dataBundle);
		assertTrue(Files.isReadable(zip));
		assertEquals(zip, dataBundle.getSource());
		checkSignature(zip);
	}

	@Test
	public void createBundle() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		assertTrue(Files.isDirectory(dataBundle.getRoot()));
		// TODO: Should this instead return a FileSystem so we can close() it?
	}

	@Test
	public void createList() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path list = DataBundles.getPort(inputs, "in1");
		DataBundles.createList(list);
		assertTrue(Files.isDirectory(list));
	}

	@Test
	public void getError() throws Exception {
		
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		DataBundles.setError(portIn1, "Something did not work", "A very\n long\n error\n trace");		
		
		ErrorDocument error = DataBundles.getError(portIn1);
		assertTrue(error.getCausedBy().isEmpty());
		
		assertEquals("Something did not work", error.getMessage());
		// Notice that the lack of trailing \n is preserved 
		assertEquals("A very\n long\n error\n trace", error.getTrace());	
		
		assertEquals(null, DataBundles.getError(null));
	}

	@Test
	public void getErrorCause() throws Exception {		
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		Path cause1 = DataBundles.setError(portIn1, "Something did not work", "A very\n long\n error\n trace");
		Path portIn2 = DataBundles.getPort(inputs, "in2");
		Path cause2 = DataBundles.setError(portIn2, "Something else did not work", "Shorter trace");
		
		
		Path outputs = DataBundles.getOutputs(dataBundle);
		Path portOut1 = DataBundles.getPort(outputs, "out1");
		DataBundles.setError(portOut1, "Errors in input", "", cause1, cause2);

		ErrorDocument error = DataBundles.getError(portOut1);
		assertEquals("Errors in input", error.getMessage());
		assertEquals("", error.getTrace());
		assertEquals(2, error.getCausedBy().size());
		
		assertTrue(Files.isSameFile(cause1, error.getCausedBy().get(0)));
		assertTrue(Files.isSameFile(cause2, error.getCausedBy().get(1)));
	}

	@Test
	public void getInputs() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		assertTrue(Files.isDirectory(inputs));
		// Second time should not fail because it already exists
		inputs = DataBundles.getInputs(dataBundle);
		assertTrue(Files.isDirectory(inputs));
		assertEquals(dataBundle.getRoot(), inputs.getParent());
	}

	@Test
	public void getList() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path list = DataBundles.getPort(inputs, "in1");
		DataBundles.createList(list);
		for (int i = 0; i < 5; i++) {
			Path item = DataBundles.newListItem(list);
			DataBundles.setStringValue(item, "test" + i);
		}
		List<Path> paths = DataBundles.getList(list);
		assertEquals(5, paths.size());
		assertEquals("test0", DataBundles.getStringValue(paths.get(0)));
		assertEquals("test4", DataBundles.getStringValue(paths.get(4)));
		
		assertEquals(null, DataBundles.getList(null));
	}

	@Test
	public void getListItem() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path list = DataBundles.getPort(inputs, "in1");
		DataBundles.createList(list);
		for (int i = 0; i < 5; i++) {
			Path item = DataBundles.newListItem(list);
			DataBundles.setStringValue(item, "item " + i);
		}
		// set at next available position
		Path item5 = DataBundles.getListItem(list, 5);
		assertTrue(item5.getFileName().toString().contains("5"));
		DataBundles.setStringValue(item5, "item 5");
	
		
		// set somewhere later
		Path item8 = DataBundles.getListItem(list, 8);
		assertTrue(item8.getFileName().toString().contains("8"));
		DataBundles.setStringValue(item8, "item 8");
		
		Path item7 = DataBundles.getListItem(list, 7);
		assertFalse(Files.exists(item7));
		assertFalse(DataBundles.isList(item7));
		assertFalse(DataBundles.isError(item7));
		assertFalse(DataBundles.isValue(item7));
		// TODO: Is it really missing? item1337 is also missing..
		assertTrue(DataBundles.isMissing(item7));
		
		
		// overwrite #2
		Path item2 = DataBundles.getListItem(list, 2);		
		DataBundles.setStringValue(item2, "replaced");
		
		
		List<Path> listItems = DataBundles.getList(list);
		assertEquals(9, listItems.size());
		assertEquals("item 0", DataBundles.getStringValue(listItems.get(0)));
		assertEquals("item 1", DataBundles.getStringValue(listItems.get(1)));
		assertEquals("replaced", DataBundles.getStringValue(listItems.get(2)));
		assertEquals("item 3", DataBundles.getStringValue(listItems.get(3)));
		assertEquals("item 4", DataBundles.getStringValue(listItems.get(4)));
		assertEquals("item 5", DataBundles.getStringValue(listItems.get(5)));
		assertNull(listItems.get(6));
		assertNull(listItems.get(7));
		assertEquals("item 8", DataBundles.getStringValue(listItems.get(8)));
		
	}

	@Test
	public void getOutputs() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path outputs = DataBundles.getOutputs(dataBundle);
		assertTrue(Files.isDirectory(outputs));
		// Second time should not fail because it already exists
		outputs = DataBundles.getOutputs(dataBundle);
		assertTrue(Files.isDirectory(outputs));
		assertEquals(dataBundle.getRoot(), outputs.getParent());
	}

	@Test
	public void getPort() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		assertFalse(Files.exists(portIn1));
		assertEquals(inputs, portIn1.getParent());
	}

	@Test
	public void getPorts() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		DataBundles.createList(DataBundles.getPort(inputs, "in1"));
		DataBundles.createList(DataBundles.getPort(inputs, "in2"));
		DataBundles.setStringValue(DataBundles.getPort(inputs, "value"),
				"A value");
		Map<String, Path> ports = DataBundles.getPorts(DataBundles
				.getInputs(dataBundle));
		assertEquals(3, ports.size());
//		System.out.println(ports);
		assertTrue(ports.containsKey("in1"));
		assertTrue(ports.containsKey("in2"));
		assertTrue(ports.containsKey("value"));

		assertEquals("A value", DataBundles.getStringValue(ports.get("value")));

	}

	@Test
	public void getReference() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		DataBundles.setReference(portIn1, URI.create("http://example.org/test"));
		URI uri = DataBundles.getReference(portIn1);
		assertEquals("http://example.org/test", uri.toASCIIString());
	}

	@Test
	public void getReferenceFromWin8() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path win8 = inputs.resolve("win8.url");
		Files.copy(getClass().getResourceAsStream("/win8.url"), win8);
				
		URI uri = DataBundles.getReference(DataBundles.getPort(inputs, "win8"));
		assertEquals("http://example.com/made-in-windows-8", uri.toASCIIString());
	}

	@Test
	public void getStringValue() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		String string = "A string";
		DataBundles.setStringValue(portIn1, string);
		assertEquals(string, DataBundles.getStringValue(portIn1));	
		assertEquals(null, DataBundles.getStringValue(null));
	}

	@Test
	public void hasInputs() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		assertFalse(DataBundles.hasInputs(dataBundle));
		DataBundles.getInputs(dataBundle); // create on demand
		assertTrue(DataBundles.hasInputs(dataBundle));
	}

	@Test
	public void hasOutputs() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		assertFalse(DataBundles.hasOutputs(dataBundle));
		DataBundles.getInputs(dataBundle); // independent
		assertFalse(DataBundles.hasOutputs(dataBundle));
		DataBundles.getOutputs(dataBundle); // create on demand
		assertTrue(DataBundles.hasOutputs(dataBundle));
	}
	
	protected boolean isEmpty(Path path) throws IOException {
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
			return !ds.iterator().hasNext();
		}
	}

	@Test
	public void isError() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		DataBundles.setError(portIn1, "Something did not work", "A very\n long\n error\n trace");		
		
		assertFalse(DataBundles.isList(portIn1));		
		assertFalse(DataBundles.isValue(portIn1));
		assertFalse(DataBundles.isMissing(portIn1));
		assertFalse(DataBundles.isReference(portIn1));
		assertTrue(DataBundles.isError(portIn1));		
	}

	@Test
	public void isList() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path list = DataBundles.getPort(inputs, "in1");
		DataBundles.createList(list);
		assertTrue(DataBundles.isList(list));
		assertFalse(DataBundles.isValue(list));
		assertFalse(DataBundles.isError(list));
		assertFalse(DataBundles.isReference(list));
		assertFalse(DataBundles.isMissing(list));
	}
	
	@Test
	public void isMissing() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		
		assertFalse(DataBundles.isList(portIn1));		
		assertFalse(DataBundles.isValue(portIn1));
		assertFalse(DataBundles.isError(portIn1));
		assertTrue(DataBundles.isMissing(portIn1));
		assertFalse(DataBundles.isReference(portIn1));
	}
	
	@Test
	public void isReference() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		DataBundles.setReference(portIn1, URI.create("http://example.org/test"));
		assertTrue(DataBundles.isReference(portIn1));
		assertFalse(DataBundles.isError(portIn1));		
		assertFalse(DataBundles.isList(portIn1));
		assertFalse(DataBundles.isMissing(portIn1));
		assertFalse(DataBundles.isValue(portIn1));
	}
	
	@Test
	public void isValue() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		DataBundles.setStringValue(portIn1, "Hello");
		assertTrue(DataBundles.isValue(portIn1));
		assertFalse(DataBundles.isList(portIn1));
		assertFalse(DataBundles.isError(portIn1));
		assertFalse(DataBundles.isReference(portIn1));
	}

	@Test
	public void listOfLists() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path list = DataBundles.getPort(inputs, "in1");
		DataBundles.createList(list);
		Path sublist0 = DataBundles.newListItem(list);
		DataBundles.createList(sublist0);
		
		Path sublist1 = DataBundles.newListItem(list);
		DataBundles.createList(sublist1);
		
		assertEquals(Arrays.asList("0/", "1/"), ls(list));
		
		DataBundles.setStringValue(DataBundles.newListItem(sublist1), 
				"Hello");
		
		assertEquals(Arrays.asList("0"), ls(sublist1));
		
		assertEquals("Hello",DataBundles.getStringValue( 
				DataBundles.getListItem(DataBundles.getListItem(list, 1), 0)));
	}

	
	
	protected List<String> ls(Path path) throws IOException {
		List<String> paths = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
			for (Path p : ds) {
				paths.add(p.getFileName() + "");
			}
		}
		Collections.sort(paths);
		return paths;
	}
	
	@Test
	public void newListItem() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path list = DataBundles.getPort(inputs, "in1");
		DataBundles.createList(list);
		Path item0 = DataBundles.newListItem(list);
		assertEquals(list, item0.getParent());
		assertTrue(item0.getFileName().toString().contains("0"));
		assertFalse(Files.exists(item0));
		DataBundles.setStringValue(item0, "test");

		Path item1 = DataBundles.newListItem(list);
		assertTrue(item1.getFileName().toString().contains("1"));
		// Because we've not actually created item1 yet
		assertEquals(item1, DataBundles.newListItem(list));
		DataBundles.setStringValue(item1, "test");

		// Check that DataBundles.newListItem can deal with gaps
		Files.delete(item0);
		Path item2 = DataBundles.newListItem(list);
		assertTrue(item2.getFileName().toString().contains("2"));

		// Check that non-numbers don't interfere
		Path nonumber = list.resolve("nonumber");
		Files.createFile(nonumber);
		item2 = DataBundles.newListItem(list);
		assertTrue(item2.getFileName().toString().contains("2"));

		// Check that extension is stripped
		Path five = list.resolve("5.txt");
		Files.createFile(five);
		Path item6 = DataBundles.newListItem(list);
		assertTrue(item6.getFileName().toString().contains("6"));
	}
	
	
	@Test
	public void safeMove() throws Exception {
		Path tmp = Files.createTempDirectory("test");
		Path f1 = tmp.resolve("f1");
		Files.createFile(f1);
		assertFalse(isEmpty(tmp));

		Bundle db = DataBundles.createBundle();
		Path f2 = db.getRoot().resolve("f2");
		DataBundles.safeMove(f1, f2);
		assertTrue(isEmpty(tmp));
		assertEquals(Arrays.asList("f2", "mimetype"), ls(db.getRoot()));

	}
	

	@Test(expected = IOException.class)
	public void safeMoveFails() throws Exception {
		Path tmp = Files.createTempDirectory("test");
		Path f1 = tmp.resolve("f1");
		Path d1 = tmp.resolve("d1");
		Files.createFile(f1);
		Files.createDirectory(d1);
		try {
			DataBundles.safeMove(f1, d1);
		} finally {
			assertTrue(Files.exists(f1));
			assertEquals(Arrays.asList("d1", "f1"), ls(tmp));
		}
	}
	
	@Test
	public void setErrorArgs() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		Path errorPath = DataBundles.setError(portIn1, "Something did not work", "A very\n long\n error\n trace");		
		assertEquals("in1.err", errorPath.getFileName().toString());

		List<String> errLines = Files.readAllLines(errorPath, Charset.forName("UTF-8"));
		assertEquals(6, errLines.size());
		assertEquals("", errLines.get(0));
		assertEquals("Something did not work", errLines.get(1));
		assertEquals("A very", errLines.get(2));
		assertEquals(" long", errLines.get(3));
		assertEquals(" error", errLines.get(4));
		assertEquals(" trace", errLines.get(5));
	}
	
	@Test
	public void setErrorCause() throws Exception {		
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		Path cause1 = DataBundles.setError(portIn1, "Something did not work", "A very\n long\n error\n trace");
		Path portIn2 = DataBundles.getPort(inputs, "in2");
		Path cause2 = DataBundles.setError(portIn2, "Something else did not work", "Shorter trace");
		
		
		Path outputs = DataBundles.getOutputs(dataBundle);
		Path portOut1 = DataBundles.getPort(outputs, "out1");
		Path errorPath = DataBundles.setError(portOut1, "Errors in input", "", cause1, cause2);
		
		List<String> errLines = Files.readAllLines(errorPath, Charset.forName("UTF-8"));
		assertEquals("../inputs/in1.err", errLines.get(0));
		assertEquals("../inputs/in2.err", errLines.get(1));
		assertEquals("", errLines.get(2));
	}
	
	@Test
	public void setErrorObj() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);

		Path portIn1 = DataBundles.getPort(inputs, "in1");
		Path cause1 = DataBundles.setError(portIn1, "a", "b");
		Path portIn2 = DataBundles.getPort(inputs, "in2");
		Path cause2 = DataBundles.setError(portIn2, "c", "d");
		
		
		Path outputs = DataBundles.getOutputs(dataBundle);
		Path portOut1 = DataBundles.getPort(outputs, "out1");

		ErrorDocument error = new ErrorDocument();
		error.getCausedBy().add(cause1);
		error.getCausedBy().add(cause2);
		
		error.setMessage("Something did not work");
		error.setTrace("Here\nis\nwhy\n");
		
		Path errorPath = DataBundles.setError(portOut1, error);		
		assertEquals("out1.err", errorPath.getFileName().toString());

		List<String> errLines = Files.readAllLines(errorPath, Charset.forName("UTF-8"));
		assertEquals(8, errLines.size());
		assertEquals("../inputs/in1.err", errLines.get(0));
		assertEquals("../inputs/in2.err", errLines.get(1));
		assertEquals("", errLines.get(2));
		assertEquals("Something did not work", errLines.get(3));
		assertEquals("Here", errLines.get(4));
		assertEquals("is", errLines.get(5));
		assertEquals("why", errLines.get(6));
		assertEquals("", errLines.get(7));
	}
	
	
	@Test
	public void setReference() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		URI uri = URI.create("http://example.org/test");		
		Path f = DataBundles.setReference(portIn1, uri);
		assertEquals("in1.url", f.getFileName().toString());
		assertEquals(inputs, f.getParent());
		assertFalse(Files.exists(portIn1));		
		
		List<String> uriLines = Files.readAllLines(f, Charset.forName("ASCII"));
		assertEquals(3, uriLines.size());
		assertEquals("[InternetShortcut]", uriLines.get(0));
		assertEquals("URL=http://example.org/test", uriLines.get(1));
		assertEquals("", uriLines.get(2));				
	}
	
	@Test
	public void setReferenceIri() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");		
		URI uri = new URI("http", "xn--bcher-kva.example.com", "/s\u00F8iland/\u2603snowman", "\u2605star");
		Path f = DataBundles.setReference(portIn1, uri);
		List<String> uriLines = Files.readAllLines(f, Charset.forName("ASCII"));
		// TODO: Double-check that this is actually correct escaping :)
		assertEquals("URL=http://xn--bcher-kva.example.com/s%C3%B8iland/%E2%98%83snowman#%E2%98%85star", 
				uriLines.get(1));
	}

	@Test
	public void setStringValue() throws Exception {
		Bundle dataBundle = DataBundles.createBundle();
		Path inputs = DataBundles.getInputs(dataBundle);
		Path portIn1 = DataBundles.getPort(inputs, "in1");
		String string = "A string";
		DataBundles.setStringValue(portIn1, string);
		assertEquals(string, Files.readAllLines(portIn1, Charset.forName("UTF-8")).get(0));
	}
	
	@Test
	public void withExtension() throws Exception {
		Path testDir = Files.createTempDirectory("test");
		Path fileTxt = testDir.resolve("file.txt");
		assertEquals("file.txt", fileTxt.getFileName().toString()); // better be!
		
		Path fileHtml = DataBundles.withExtension(fileTxt, ".html");
		assertEquals(fileTxt.getParent(), fileHtml.getParent());
		assertEquals("file.html", fileHtml.getFileName().toString()); 
		
		Path fileDot = DataBundles.withExtension(fileTxt, ".");
		assertEquals("file.", fileDot.getFileName().toString()); 
		
		Path fileEmpty = DataBundles.withExtension(fileTxt, "");
		assertEquals("file", fileEmpty.getFileName().toString()); 
		
		
		Path fileDoc = DataBundles.withExtension(fileEmpty, ".doc");
		assertEquals("file.doc", fileDoc.getFileName().toString());
		
		Path fileManyPdf = DataBundles.withExtension(fileTxt, ".test.many.pdf");
		assertEquals("file.test.many.pdf", fileManyPdf.getFileName().toString()); 
		
		Path fileManyTxt = DataBundles.withExtension(fileManyPdf, ".txt");
		assertEquals("file.test.many.txt", fileManyTxt.getFileName().toString());
	}

}

