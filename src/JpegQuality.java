import java.awt.image.*;
import java.io.*;
import javax.imageio.*;

public class JpegQuality {

    /* Fitted parameters. */
    private static double alpha = -245.8909;
    private static double beta = 261.9373;
    private static double gamma1 = -0.02398886;
    private static double gamma2 = 0.01601664;
    private static double gamma3 = 0.00642859;

    private static void printMatrix(int [][] matrix, int width, int height) {
	for (int i = 0; i < height; i++) {
	    for (int j = 0; j < width; j++) {
		System.out.print("" + matrix[i][j] + "\t");
	    }
	    System.out.println();
	}
    }

    private static void printMatrix(double [][] matrix, int width, int height) {
	for (int i = 0; i < height; i++) {
	    for (int j = 0; j < width; j++) {
		System.out.print("" + matrix[i][j] + "\t");
	    }
	    System.out.println();
	}
    }

    private static BufferedImage loadImage(String filename) {
	BufferedImage img = null;
	try {
	    img = ImageIO.read(new File(filename));
	} catch (IOException e) {
	    
	}
	return img;
    }
    
    private static double grayscale(int rgb) {
	final int BITS_PER_CHANNEL = 8;
	final int MASK = (1 << BITS_PER_CHANNEL) - 1;
	int b = rgb & MASK;
	rgb = rgb >> BITS_PER_CHANNEL;
	int g = rgb & MASK;
	rgb = rgb >> BITS_PER_CHANNEL;
	int r = rgb & MASK;
     	double gray = 0.299 * r + 0.587 * g + 0.114 * b;
	return gray;
    }

    private static double computeB(double[][] differencingSignal, boolean horizontal) {
	int height = differencingSignal.length;
	int width = differencingSignal[0].length;
	int beginHorizontal = 0;
	int beginVertical = 7;
	int stepsHorizontal = 1;
	int stepsVertical = 8;
	if (horizontal) {
	    beginHorizontal = 7;
	    beginVertical = 0;
	    stepsHorizontal = 8;
	    stepsVertical = 1;
	}
	int terms = 0;
	double sum = 0.0;
	for (int i = beginVertical; i < height; i += stepsVertical) {
	    for (int j = beginHorizontal; j < width; j += stepsHorizontal) {
		sum += Math.abs(differencingSignal[i][j]);
		terms++;
	    }
	}
	assert(terms == height * (Math.floor(width / 8) - 1));
	return sum / terms;
    }

    private static double computeA(double[][] differencingSignal, double b) {
	double sum = 0.0;
	int height = differencingSignal.length;
	int width = differencingSignal[0].length;
	for (int i = 0; i < height; i++) {
	    for (int j = 0; j < width; j++) {
		sum += Math.abs(differencingSignal[i][j]);
	    }
	}
	double result = ((8.0 * sum / height / width) - b) / 7.0;
	return result;
    }

    private static double computeZ(int[][] zeroCrossing) {
	double sum = 0.0;
	int height = zeroCrossing.length;
	int width = zeroCrossing[0].length;
	for (int i = 0; i < height; i++) {
	    for (int j = 0; j < width; j++) {
		sum += zeroCrossing[i][j];
	    }
	}
	return 1.0 * sum / width / height;
    }

    private static double average(double horizontal, double vertical) {
	return (horizontal + vertical) / 2.0;
    }

    private static double quality(double alpha, double beta, double gamma1, double gamma2, double gamma3, double a, double b, double z) {
	return alpha + beta * Math.pow(b, gamma1) * Math.pow(a, gamma2) * Math.pow(z, gamma3);
    }

    public static void main(String[] args) {
	boolean verbose = false;
	if (args.length < 1) {
	    System.out.println("Usage: java JpegQuality <filename.jpg>");
	    return;
	}
	String filename = args[0];
	System.out.print("Estimated quality of " + filename + " is ");
	
	// TODO: optionally override fitted parameters.

	// read image into matrix
	BufferedImage img = loadImage(filename);
	int width = img.getWidth();
	int height = img.getHeight();
	
	// extract grayscales
	double[][] grayscaleImage = new double[height][width];
	for (int i = 0; i < height; i++) {
	    for (int j = 0; j < width; j++) {
		// All matrices are indexed by (row, column). Images are index by (column, row).
		grayscaleImage[i][j] = grayscale(img.getRGB(j, i));
	    }
	}
	
	// compute horizontal and vertical differencing signal
	double[][] differencingSignalHorizontal = new double[height][width - 1];
	double[][] differencingSignalVertical = new double[height - 1][width];
	for (int i = 0; i < height; i++) {
	    for (int j = 0; j < width; j++) {
		if (j < width - 1) {
		    differencingSignalHorizontal[i][j] = grayscaleImage[i][j+1]
			- grayscaleImage[i][j];
		}
		if (i < height - 1) {
		    differencingSignalVertical[i][j] = grayscaleImage[i+1][j]
			- grayscaleImage[i][j];
		}
	    }
	}
    
	// compute zerocrossing
	int[][] zeroCrossingHorizontal = new int[height][width - 2];
	int[][] zeroCrossingVertical = new int[height - 2][width];
	for (int i = 0; i < height; i++) {
	    for (int j = 0; j < width; j++) {
		if (j < width - 2) {
		    zeroCrossingHorizontal[i][j] = (differencingSignalHorizontal[i][j] * differencingSignalHorizontal[i][j+1]) < 0 ? 1 : 0;
		}
		if (i < height - 2) {
		    zeroCrossingVertical[i][j] = (differencingSignalVertical[i][j] * differencingSignalVertical[i+1][j]) < 0 ? 1 : 0;
		}
	    }
	}

	// compute Ah, Av, Bh, Bv, Zh, Zv
	double bHorizontal = computeB(differencingSignalHorizontal, true);
	double bVertical = computeB(differencingSignalVertical, false);
	double aHorizontal = computeA(differencingSignalHorizontal, bHorizontal);
	double aVertical = computeA(differencingSignalVertical, bVertical);
	double zHorizontal = computeZ(zeroCrossingHorizontal);
	double zVertical = computeZ(zeroCrossingVertical);

	// debug info
	if (verbose) {
	    printMatrix(grayscaleImage, 3, 3);
	    printMatrix(differencingSignalHorizontal, 3, 3);
	    printMatrix(differencingSignalVertical, 3, 3);
	    printMatrix(zeroCrossingHorizontal, 3, 3);
	    printMatrix(zeroCrossingVertical, 3, 3);
	    System.out.println("Ah: " + aHorizontal + ", Av: " + aVertical
			       + "\nBh: " + bHorizontal + ", Bv: " + bVertical
			       + "\nZh: " + zHorizontal + ", Zv: " + zVertical);
	}
	
	// compute A, B, Z
	double a = average(aHorizontal, aVertical);
	double b = average(bHorizontal, bVertical);
	double z = average(zHorizontal, zVertical);
	//System.out.println("a, b, z computed");

	// compute S
	double quality = quality(alpha, beta, gamma1, gamma2, gamma3, a, b, z);
	System.out.println("" + Math.round(quality) + "/10");
    }
}
