public class TestHessianDelta {
    public static void main(String[] args) {
        double machineEpsilon = Math.ulp(1.0);
        double hessianDelta = Math.pow(machineEpsilon, 1.0/3.0);
        
        System.out.println("Machine epsilon (double precision): " + machineEpsilon);
        System.out.println("HESSIAN_DELTA = ε^(1/3): " + hessianDelta);
        System.out.println("Previous magic number: 1e-5");
        System.out.println("Ratio (new/old): " + (hessianDelta / 1e-5));
    }
}
