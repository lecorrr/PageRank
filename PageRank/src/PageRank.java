import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class PageRank {

	public static class PageRankJob1Mapper extends Mapper<LongWritable, Text, Text, Text> {
		
		@Override
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

			if (value.charAt(0) != '#') {

				int tabIndex = value.find("\t");
				String nodeA = Text.decode(value.getBytes(), 0, tabIndex);
				String nodeB = Text.decode(value.getBytes(), tabIndex + 1, value.getLength() - (tabIndex + 1));
				context.write(new Text(nodeA), new Text(nodeB));

				PageRank.NODES.add(nodeA);
				PageRank.NODES.add(nodeB);

			}
		}
	}

	public static class PageRankJob1Reducer extends Reducer<Text, Text, Text, Text> {

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

			boolean first = true;
			String links = (PageRank.DAMPING / PageRank.NODES.size()) + "\t";

			for (Text value : values) {
				if (!first)
					links += ",";
				links += value.toString();
				first = false;
			}

			context.write(key, new Text(links));
		}

	}

	public class PageRankJob2Mapper extends Mapper<LongWritable, Text, Text, Text> {

		@Override
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

			int tIdx1 = value.find("\t");
			int tIdx2 = value.find("\t", tIdx1 + 1);

			String page = Text.decode(value.getBytes(), 0, tIdx1);
			String pageRank = Text.decode(value.getBytes(), tIdx1 + 1, tIdx2 - (tIdx1 + 1));
			String links = Text.decode(value.getBytes(), tIdx2 + 1, value.getLength() - (tIdx2 + 1));

			String[] allOtherPages = links.split(",");
			for (String otherPage : allOtherPages) {
				Text pageRankWithTotalLinks = new Text(pageRank + "\t" + allOtherPages.length);
				context.write(new Text(otherPage), pageRankWithTotalLinks);
			}

			context.write(new Text(page), new Text(PageRank.LINKS_SEPARATOR + links));

		}

	}

	public class PageRankJob2Reducer extends Reducer<Text, Text, Text, Text> {

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

			String links = "";
			double sumShareOtherPageRanks = 0.0;

			for (Text value : values) {

				String content = value.toString();

				if (content.startsWith(PageRank.LINKS_SEPARATOR)) {
					links += content.substring(PageRank.LINKS_SEPARATOR.length());
				} else {

					String[] split = content.split("\\t");

					double pageRank = Double.parseDouble(split[0]);
					int totalLinks = Integer.parseInt(split[1]);

					sumShareOtherPageRanks += (pageRank / totalLinks);
				}

			}

			double newRank = PageRank.DAMPING * sumShareOtherPageRanks + (1 - PageRank.DAMPING);
			context.write(key, new Text(newRank + "\t" + links));

		}

	}

	public class PageRankJob3Mapper extends Mapper<LongWritable, Text, DoubleWritable, Text> {

		@Override
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

			int tIdx1 = value.find("\t");
			int tIdx2 = value.find("\t", tIdx1 + 1);

			String page = Text.decode(value.getBytes(), 0, tIdx1);
			float pageRank = Float.parseFloat(Text.decode(value.getBytes(), tIdx1 + 1, tIdx2 - (tIdx1 + 1)));

			context.write(new DoubleWritable(pageRank), new Text(page));

		}

	}

	private static final String KEY_DAMPING = "--damping";
	private static final String KEY_DAMPING_ALIAS = "-d";

	private static final String KEY_COUNT = "--count";
	private static final String KEY_COUNT_ALIAS = "-c";

	private static final String KEY_INPUT = "--input";
	private static final String KEY_INPUT_ALIAS = "-i";

	private static final String KEY_OUTPUT = "--output";
	private static final String KEY_OUTPUT_ALIAS = "-o";

	private static final String KEY_HELP = "--help";
	private static final String KEY_HELP_ALIAS = "-h";

	public static NumberFormat NF = new DecimalFormat("00");
	public static Set<String> NODES = new HashSet<String>();
	public static String LINKS_SEPARATOR = "|";

	public static Double DAMPING = 0.85;
	public static int ITERATIONS = 2;
	public static String IN_PATH = "";
	public static String OUT_PATH = "";

	public static void main(String[] args) throws Exception {

		try {

			for (int i = 0; i < args.length; i += 2) {

				String key = args[i];
				String value = args[i + 1];

				if (key.equals(KEY_DAMPING) || key.equals(KEY_DAMPING_ALIAS)) {
					PageRank.DAMPING = Math.max(Math.min(Double.parseDouble(value), 1.0), 0.0);
				} else if (key.equals(KEY_COUNT) || key.equals(KEY_COUNT_ALIAS)) {
					PageRank.ITERATIONS = Math.max(Integer.parseInt(value), 1);
				} else if (key.equals(KEY_INPUT) || key.equals(KEY_INPUT_ALIAS)) {
					PageRank.IN_PATH = value.trim();
					if (PageRank.IN_PATH.charAt(PageRank.IN_PATH.length() - 1) == '/')
						PageRank.IN_PATH = PageRank.IN_PATH.substring(0, PageRank.IN_PATH.length() - 1);
				} else if (key.equals(KEY_OUTPUT) || key.equals(KEY_OUTPUT_ALIAS)) {
					PageRank.OUT_PATH = value.trim();
					if (PageRank.OUT_PATH.charAt(PageRank.OUT_PATH.length() - 1) == '/')
						PageRank.OUT_PATH = PageRank.OUT_PATH.substring(0, PageRank.IN_PATH.length() - 1);
				} else if (key.equals(KEY_HELP) || key.equals(KEY_HELP_ALIAS)) {
					printUsageText(null);
					System.exit(0);
				}
			}

		} catch (ArrayIndexOutOfBoundsException e) {
			printUsageText(e.getMessage());
			System.exit(1);
		} catch (NumberFormatException e) {
			printUsageText(e.getMessage());
			System.exit(1);
		}

		if (PageRank.IN_PATH.isEmpty() || PageRank.OUT_PATH.isEmpty()) {
			printUsageText("missing required parameters");
			System.exit(1);
		}

		FileSystem fs = FileSystem.get(new Configuration());
		if (fs.exists(new Path(PageRank.OUT_PATH)))
			fs.delete(new Path(PageRank.OUT_PATH), true);

		System.out.println("Damping factor: " + PageRank.DAMPING);
		System.out.println("Number of iterations: " + PageRank.ITERATIONS);
		System.out.println("Input directory: " + PageRank.IN_PATH);
		System.out.println("Output directory: " + PageRank.OUT_PATH);
		System.out.println("---------------------------");

		Thread.sleep(1000);

		String inPath = null;
		;
		String lastOutPath = null;
		PageRank pagerank = new PageRank();

		System.out.println("Running Job#1 (graph parsing) ...");
		boolean isCompleted = pagerank.job1(IN_PATH, OUT_PATH + "/iter00");
		if (!isCompleted) {
			System.exit(1);
		}

		for (int runs = 0; runs < ITERATIONS; runs++) {
			inPath = OUT_PATH + "/iter" + NF.format(runs);
			lastOutPath = OUT_PATH + "/iter" + NF.format(runs + 1);
			System.out.println(
					"Running Job#2 [" + (runs + 1) + "/" + PageRank.ITERATIONS + "] (PageRank calculation) ...");
			isCompleted = pagerank.job2(inPath, lastOutPath);
			if (!isCompleted) {
				System.exit(1);
			}
		}

		System.out.println("Running Job#3 (rank ordering) ...");
		isCompleted = pagerank.job3(lastOutPath, OUT_PATH + "/result");
		if (!isCompleted) {
			System.exit(1);
		}

		System.out.println("DONE!");
		System.exit(0);
	}

	public boolean job1(String in, String out) throws IOException, ClassNotFoundException, InterruptedException {

		Job job = Job.getInstance(new Configuration(), "Job #1");
		job.setJarByClass(PageRank.class);

		FileInputFormat.addInputPath(job, new Path(in));
		job.setInputFormatClass(TextInputFormat.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setMapperClass(PageRankJob1Mapper.class);

		FileOutputFormat.setOutputPath(job, new Path(out));
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setReducerClass(PageRankJob1Reducer.class);

		return job.waitForCompletion(true);

	}

	public boolean job2(String in, String out) throws IOException, ClassNotFoundException, InterruptedException {

		Job job = Job.getInstance(new Configuration(), "Job #2");
		job.setJarByClass(PageRank.class);

		FileInputFormat.setInputPaths(job, new Path(in));
		job.setInputFormatClass(TextInputFormat.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setMapperClass(PageRankJob2Mapper.class);

		FileOutputFormat.setOutputPath(job, new Path(out));
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setReducerClass(PageRankJob2Reducer.class);

		return job.waitForCompletion(true);

	}

	public boolean job3(String in, String out) throws IOException, ClassNotFoundException, InterruptedException {

		Job job = Job.getInstance(new Configuration(), "Job #3");
		job.setJarByClass(PageRank.class);

		FileInputFormat.setInputPaths(job, new Path(in));
		job.setInputFormatClass(TextInputFormat.class);
		job.setMapOutputKeyClass(DoubleWritable.class);
		job.setMapOutputValueClass(Text.class);
		job.setMapperClass(PageRankJob3Mapper.class);

		FileOutputFormat.setOutputPath(job, new Path(out));
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setOutputKeyClass(DoubleWritable.class);
		job.setOutputValueClass(Text.class);

		return job.waitForCompletion(true);

	}

	public static void printUsageText(String err) {

		if (err != null) {
			System.err.println("ERROR: " + err + ".\n");
		}

		System.out.println("Usage: pagerank.jar " + KEY_INPUT + " <input> " + KEY_OUTPUT + " <output>\n");
		System.out.println("Options:\n");
		System.out.println("    " + KEY_INPUT + "    (" + KEY_INPUT_ALIAS
				+ ")    <input>       The directory of the input graph [REQUIRED]");
		System.out.println("    " + KEY_OUTPUT + "   (" + KEY_OUTPUT_ALIAS
				+ ")    <output>      The directory of the output result [REQUIRED]");
		System.out.println(
				"    " + KEY_DAMPING + "  (" + KEY_DAMPING_ALIAS + ")    <damping>     The damping factor [OPTIONAL]");
		System.out.println("    " + KEY_COUNT + "    (" + KEY_COUNT_ALIAS
				+ ")    <iterations>  The amount of iterations [OPTIONAL]");
		System.out
				.println("    " + KEY_HELP + "     (" + KEY_HELP_ALIAS + ")                  Display the help text\n");
	}

}