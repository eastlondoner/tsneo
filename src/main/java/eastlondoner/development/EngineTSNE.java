package eastlondoner.development;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.nd4j.linalg.ops.transforms.Transforms.log;
import static org.nd4j.linalg.ops.transforms.Transforms.pow;
import static org.nd4j.linalg.ops.transforms.Transforms.exp;



public class EngineTSNE {
    private final int N;
    private float perplexityTarget;
    private int perplexityIterations;
    private float lambda_kullback_leibler = 1;
    private float learning_rate = 100f;
    private float lambda_compression = 1;
    private float lambda_entropy = 0;
    private int OutDimensions = 3;

    private static final double MAX_INT = (double)Integer.MAX_VALUE;
    private static final double MIN_INT = (double)Integer.MIN_VALUE;
    private int gradientDescentIterations = 9200;
    private float floor = 10000f;
    private double early_accel = 100;

    public class Lazy<T> implements Supplier<T> {
        private Supplier<T> fn;

        public Lazy(Supplier<T> fn){

            this.fn = () -> {
                T val = fn.get();
                this.fn = () -> val;
                return val;
            };
        }
        public T get() { return fn.get(); }
    }


    private INDArray distanceArray;
    private final INDArray sigmaVector;
    private final INDArray reducedMatrix;
    private final Lazy<INDArray> distanceSquaredArray = new Lazy<>(() -> pow(this.distanceArray,2, true));


    public EngineTSNE(INDArray distanceArray, float perplexityTarget){
        this.distanceArray = distanceArray;
        this.N = distanceArray.shape()[0];
        this.perplexityTarget = perplexityTarget;
        this.perplexityIterations = 100;
        this.sigmaVector = Nd4j.ones(this.N, 1);
        this.reducedMatrix = Nd4j.rand(new int[]{OutDimensions, N}, 32);
    }

    public static boolean between(float a, float b, float query){
        return query > a && query <= b || query > b && query <= a;
    }

    public static Pair<Float, Pair<Float, Float>> binarySearch(Function<Float, Float> method, float target, Pair<Float, Float> bounds){
        float left = bounds.getLeft();
        float right = bounds.getRight();
        float v1 = method.apply(left);
        float v2 = method.apply(right);
        float midPoint = left + ((right - left) / 2);
        float vmid = method.apply(midPoint);
        if(Float.isNaN(v1)){
            v1 = 0;
        }

        if(between(v1, vmid, target)){
            return new ImmutablePair<>(vmid, new ImmutablePair<>(left, midPoint));
        } else if (between(vmid, v2, target)) {
            return new ImmutablePair<>(vmid, new ImmutablePair<>(midPoint, right));
        } else {
            throw new RuntimeException("Invalid state for binary search");
        }
    }

    public void optimiseSigmaForI(int i){
        Pair<Float, Pair<Float, Float>> res = new ImmutablePair<>(null, new ImmutablePair<>(0.01f, (float)N));
        for (int j = 0; j < perplexityIterations; j++) {
            Pair<Float, Float> bounds = res.getRight();

            res = binarySearch((sigma) -> {
                sigmaVector.putScalar(i, 0, sigma);

                double perplexity = getPerplexity( i );
                return (float)perplexity;
            }, perplexityTarget, bounds);

            float current = res.getLeft();

            if(Math.abs((current - perplexityTarget) / perplexityTarget) < 0.001) {
             sigmaVector.putScalar(i, 0, bounds.getLeft() + ((bounds.getLeft() - bounds.getRight()) / 2));
             return;
            }
        }
        throw new RuntimeException("Unable to converge on perplexity");
    }

    public INDArray getPDist(){
        for (int i = 0; i < N; i++) {
            optimiseSigmaForI(i);
        }

        INDArray outputMatrix = Nd4j.zeros(N,N);

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                outputMatrix.put(i, j, pJGivenI(j, i));
            }
        }

        return outputMatrix;
    }

    public INDArray run(){
        INDArray pDistMatrix = getPDist();
        Float Cold = null;
        Float C = null;
        Float stepsize;
        INDArray pMatrix = pIJ( pDistMatrix );
        INDArray gradients;
        INDArray previousGradients = Nd4j.zeros( this.reducedMatrix.shape() );
        float momentum = 0.8f;
        for ( int i = 0; i < this.gradientDescentIterations; i++ )
        {
            previousGradients.muli( momentum );

            reducedMatrix.subi(previousGradients);
            gradients = getGradients(pMatrix);
            stepsize = gradients.transpose().mmul( gradients ).getFloat( 0,0 );
            gradients.muli( learning_rate );
            previousGradients = gradients;
            updateYs(gradients);

            if(C != null){
                Cold = C;
            }

            if(i % 10 == 0)
            {
                C = tFn( pMatrix );
                if ( Cold != null )
                {

                    System.out.printf( "Cost function was %s, now %s\n", Cold, C );
                    System.out.printf( "Step size %s\n", stepsize );
                    float improvement = (C - Cold) / C;
                    System.out.printf( "Improvement %s\n", improvement );
                    System.out.printf( "Learning rate %s\n", learning_rate );
                    System.out.printf( "early accel %s\n", early_accel );
                    if(improvement > 0) {
                        early_accel = 0;
                        previousGradients = Nd4j.zeros( this.reducedMatrix.shape() );
                        if (learning_rate > floor) {
                            learning_rate = floor;
                        }
                        learning_rate = learning_rate/2;
                        floor = learning_rate;
                    } else if (improvement > -0.01 && learning_rate < 1.5 * floor) {
                        learning_rate = 1.2f * learning_rate;
                    } else {
                        learning_rate = 1.01f * learning_rate;
                    }
                }
                if ( C < 0.1 )
                {
                    break;
                }
            } else {
                System.out.printf( "%s\n", i);
            }
        }
        return reducedMatrix;
    }


    private void updateYs( INDArray gradients )
    {
        reducedMatrix.subi(gradients);
    }

    private INDArray getGradients(INDArray pMatrix) {
        INDArray klgrad = getKullbackLeiblerGradients(pMatrix);

        return klgrad;
    }

    private float getCosgetCostFn(INDArray pMatrix)
    {
        float klcost = getKLCost( pMatrix );
        return klcost;
    }

    private float getKLCost(INDArray pDistMatrix){
        INDArray onePlusDSqrMat = calculateOnePlusDistanceSquaredMatrix();
        INDArray qMatrix = calculateQMatrix( onePlusDSqrMat );
        INDArray Pij;
        float update;
        float c = 0;
        for ( int i = 0; i < N; i++ )
        {
            for ( int j = 0; j < N; j++ )
            {
                if(i==j){
                    continue;
                }
                Pij = pDistMatrix.getScalar( i,j );

                update = Pij.mul(log( Pij.div(qMatrix.getScalar( i, j )))).getFloat( 0,0 );
                c += update;
            }
        }
        return c;
    }

    private INDArray getKullbackLeiblerGradients( INDArray pMatrix)
    {
        INDArray onePlusDSqrMat = calculateOnePlusDistanceSquaredMatrix();
        INDArray qMatrix = calculateQMatrix( onePlusDSqrMat );
        float scalar = lambda_kullback_leibler * 4;
        INDArray KLMat = Nd4j.zeros(this.OutDimensions, N);
        INDArray P_Minus_Q = pMatrix.sub( qMatrix );
        INDArray KLi;
        INDArray Yi;
        INDArray Yj;
        INDArray Yi_Yj = Nd4j.zeros( OutDimensions, 1 );
        INDArray denominator;
        for ( int i = 0; i < N; i++ )
        {
            KLi = KLMat.getColumn( i );
            Yi = this.reducedMatrix.getColumn( i );
            for ( int j = 0; j < N; j++ )
            {

                Yj = this.reducedMatrix.getColumn( j );
                Yi_Yj.assign( Yi.sub(Yj) );
                denominator = onePlusDSqrMat.getScalar(i, j);
                Yi_Yj.muli((P_Minus_Q.getScalar( i,j ).div(denominator)));
                KLi.addi(Yi_Yj);
            }

        }
        KLMat.muli(scalar);
        return KLMat;
    }

    public static double log2( double val )
    {
        return Math.log(val) / Math.log(2);
    }

    public double getPerplexity(int i){
        double exponent = 0;

        for (int k = 0; k < N ; k++) {
            if(i == k){
                continue;
            }
            double pki = pJGivenI(k, i);
            exponent += pki * log2(pki);
        }
        return Math.pow(2,-exponent);
    }

    public double pJGivenI(int j, int i){
        if(i == j){
            return 0;
        }

        INDArray dSquaredMat = distanceSquaredArray.get();

        double dSquared = dSquaredMat.getDouble(i,j);
        double sigmaSquared = Math.pow(sigmaVector.getDouble(i), 2);

        double numerator = Math.exp(-dSquared / (2 * sigmaSquared));
        double denominator = 0;

        //TODO: do this with matrix ops
        for (int k = 0; k < N; k++) {
            if(k == i){
                continue;
            }
            denominator += Math.exp(-dSquaredMat.getDouble(i,k) / (2 * sigmaSquared));
        }

        double val =  numerator / denominator;
        return val;
    }

    private INDArray pIJ(INDArray outputMatrix){
        return outputMatrix.transpose().add( outputMatrix ).div( N * 2 );
    }

    private INDArray calculateOnePlusDistanceSquaredMatrix(){
        INDArray onePlusDSqr = Nd4j.zeros(N, N);
        for ( int i = 0; i < N; i++ )
        {
            // TODO this is symmetric optimize so we dont do everything twice
            for ( int j = 0; j < N; j++ )
            {
                if(i == j) {
                    continue;
                }
                onePlusDSqr.putScalar( i, j, OnePlusDistanceSquared( i, j ) );
            }
        }
        return onePlusDSqr;
    }

    private INDArray calculateQMatrix(INDArray onePlusDSqr){
        float denominator = 0;
        INDArray qMatrix = Nd4j.zeros(N, N);
        for ( int i = 0; i < N; i++ )
        {
            // TODO this is symmetric optimize so we dont do everything twice
            for ( int j = 0; j < N; j++ )
            {
                if(i == j) {
                    continue;
                }
                float qDash = qFnIJ(onePlusDSqr, i, j );
                qMatrix.putScalar( i, j, qDash );

                denominator += qDash;
            }
        }
        qMatrix.divi(denominator);
        return qMatrix;
    }

    private float OnePlusDistanceSquared(int i, int j){
        INDArray Yi = this.reducedMatrix.getColumn( i );
        INDArray Yj = this.reducedMatrix.getColumn( j );
        INDArray Yi_Yj = Yi.sub( Yj );
        INDArray dot = Yi_Yj.transpose().mmul( Yi_Yj );
        return 1 + dot.getFloat( 0, 0 );
    }

    private float qFnIJ(INDArray onePlusDSqr, int i, int j){
        return 1 / onePlusDSqr.getFloat( i, j );
    }
}
