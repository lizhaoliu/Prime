package prime.math;

/**
 * 
 * @author lizhaoliu
 * 
 */
public class Point2 {
    public int x, y;

    public Point2() {
	x = y = 0;
    }

    public Point2(int x, int y) {
	set(x, y);
    }

    public void set(int x, int y) {
	this.x = x;
	this.y = y;
    }
}
