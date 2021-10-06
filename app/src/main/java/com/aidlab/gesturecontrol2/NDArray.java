package com.aidlab.gesturecontrol2;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;

class NDArray<T> {

    final TypedArray<T> data;
    public int[] shape;
    Offset[] offsets;

    public NDArray(int[] shape) {
        this.data = new TypedArray<>(shape);
        this.offsets = null;
        this.shape = shape;
    }

    public NDArray(TypedArray<T> data, Offset[] offsets) {
        this.data = data;
        this.offsets = offsets;
        this.shape = shape();
    }

    public static Index index(int ind) {
        return new Index(ind);
    }

    public static Slice slice() {
        return new Slice(null, null, 1);
    }

    public static Slice slice(Integer start, Integer stop) {
        return new Slice(start, stop, 1);
    }

    public static Slice slice(Integer start, Integer stop, Integer step) {
        return new Slice(start, stop, step);
    }

    public static <T> NDArray<T> array(Object[] arr) {
        List<Integer> shapeList = new ArrayList<>();
        shapeList.add(arr.length);

        Object[] nested = arr;
        while (nested[0] instanceof Object[]) {
            nested = (Object[]) nested[0];
            shapeList.add(nested.length);
        }

        int[] shape = new int[shapeList.size()];
        for (int i = 0; i < shape.length; i++) shape[i] = shapeList.get(i);

        NDArray<T> ndarray = new NDArray<>(shape);
        deepCopy(arr, ndarray, 0, 0);
        return ndarray;
    }

    @SuppressWarnings("unchecked")
    public static <T> void deepCopy(Object[] arr, NDArray<T> ndarray, int dim, int start) {
        for (int i = 0; i < arr.length; i++) {
            if (dim < ndarray.shape.length - 1) {
                deepCopy((Object[]) arr[i], ndarray, dim + 1, start + i * ndarray.length(dim + 1));
            } else {
                ndarray.data(start + i, (T) arr[i]);
            }
        }
    }

    static public <T, E> NDArray<E> apply(NDArray<T> a, NDArray<T> b, BiFunction<T, T, E> func) {
        Broadcast<T> bc = new Broadcast<>(a, b);
        if (bc.shape == null) return null;
        NDArray<E> out = new NDArray<>(bc.shape);
        for (int i = 0; bc.hasNext(); i++) {
            int[][] indices = bc.next();
            out.data(i, func.apply(a.getValue(indices[0]), b.getValue(indices[1])));
        }
        return out;
    }

    static public NDArray<Double> add(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::add);
    }

    static public NDArray<Double> sub(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::sub);
    }

    static public NDArray<Double> mul(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::mul);
    }

    static public NDArray<Double> div(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::div);
    }

    static public NDArray<Boolean> gt(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::gt);
    }

    static public NDArray<Boolean> gte(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::gte);
    }

    static public NDArray<Boolean> lt(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::lt);
    }

    static public NDArray<Boolean> lte(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::lte);
    }

    static public NDArray<Boolean> eq(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::eq);
    }

    static public NDArray<Boolean> neq(NDArray<Double> a, NDArray<Double> b) {
        return apply(a, b, Operator::neq);
    }

    static public NDArray<Double> zeros(int[] shape){
        NDArray<Double> ndarray = new NDArray<>(shape);
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            ndarray.setValue(indices, 0.0);
        }
        return ndarray;
    }

    static public Boolean all(NDArray<Boolean> ndarray){
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            if(!ndarray.getValue(indices)) return false;
        }
        return true;
    }

    static public Boolean any(NDArray<Boolean> ndarray){
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            if(ndarray.getValue(indices)) return true;
        }
        return false;
    }

    static public NDArray<Double> ones(int[] shape){
        NDArray<Double> ndarray = new NDArray<>(shape);
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            ndarray.setValue(indices, 1.0);
        }
        return ndarray;
    }

    static Random rand = new Random();
    static public NDArray<Double> random(int[] shape){
        NDArray<Double> ndarray = new NDArray<>(shape);
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            ndarray.setValue(indices, rand.nextDouble());
        }
        return ndarray;
    }

    static public Double amin(NDArray<Double> ndarray){
        Double min = null;
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            Double value = ndarray.getValue(indices);
            if(min == null) min = value;
            else min = Math.min(min, value);
        }
        return min;
    }

    static public NDArray<Double> amin(NDArray<Double> ndarray, int axis){
        int[] shape = new int[ndarray.shape.length - 1];
        Selection[] selections = new Selection[ndarray.shape.length];
        for (int i = 0, j = 0; i < ndarray.shape.length; i++) {
            if(axis != i){
                shape[j] = ndarray.shape[i];
                j++;
            }
        }
        NDArray<Double> out = new NDArray<>(shape);

        int[] outIndices = new int[out.shape.length];
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            if(indices[axis] != 0) continue;
            for (int i = 0, j=0; i < selections.length; i++) {
                if(i == axis) selections[i] = slice();
                else{
                    selections[i] = index(indices[i]);
                    outIndices[j] = indices[i];
                    j++;
                }
            }
            out.setValue(outIndices, amin(ndarray.get(selections)));
        }
        return out;
    }

    static public NDArray<Double> nan_to_num(NDArray<Double> ndarray){
        NDArray<Double> out = ndarray.copy();
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            Double value = ndarray.getValue(indices);
            if(value == null || Double.isNaN(value))
                value = 0.0;
            out.setValue(indices, value);
        }
        return out;
    }

    static public NDArray<Double> mean(NDArray<Double> ndarray, int axis){
        int[] shape = new int[ndarray.shape.length - 1];
        Selection[] selections = new Selection[ndarray.shape.length];
        for (int i = 0, j = 0; i < ndarray.shape.length; i++) {
            if(axis != i){
                shape[j] = ndarray.shape[i];
                j++;
            }
        }
        NDArray<Double> out = new NDArray<>(shape);

        int[] outIndices = new int[out.shape.length];
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            if(indices[axis] != 0) continue;
            for (int i = 0, j=0; i < selections.length; i++) {
                if(i == axis) selections[i] = slice();
                else{
                    selections[i] = index(indices[i]);
                    outIndices[j] = indices[i];
                    j++;
                }
            }
            out.setValue(outIndices, mean(ndarray.get(selections)));
        }
        return out;
    }


    static public NDArray<Double> abs(NDArray<Double> ndarray){
        NDArray<Double> out = new NDArray<>(ndarray.shape);
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            Double value = ndarray.getValue(indices);
            out.setValue(indices, Math.abs(value));
        }
        return out;
    }

    static public Double mean(NDArray<Double> ndarray){
        Double total = 0.0;
        int count=0;
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            Double value = ndarray.getValue(indices);
            total += value;
            count++;
        }
        if(count>0)
            return total / count;
        return null;
    }

    static public Double sum(NDArray<Double> ndarray){
        Double total = 0.0;
        int count=0;
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            Double value = ndarray.getValue(indices);
            total += value;
            count++;
        }
        return total;
    }

    static public NDArray<Double> amax(NDArray<Double> ndarray, int axis){
        int[] shape = new int[ndarray.shape.length - 1];
        Selection[] selections = new Selection[ndarray.shape.length];
        for (int i = 0, j = 0; i < ndarray.shape.length; i++) {
            if(axis != i){
                shape[j] = ndarray.shape[i];
                j++;
            }
        }
        NDArray<Double> out = new NDArray<>(shape);

        int[] outIndices = new int[out.shape.length];
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            if(indices[axis] != 0) continue;
            for (int i = 0, j=0; i < selections.length; i++) {
                if(i == axis) selections[i] = slice();
                else{
                    selections[i] = index(indices[i]);
                    outIndices[j] = indices[i];
                    j++;
                }
            }
            out.setValue(outIndices, amax(ndarray.get(selections)));
        }
        return out;
    }

    static public Double amax(NDArray<Double> ndarray){
        Double max = null;
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            Double value = ndarray.getValue(indices);
            if(max == null) max = value;
            else max = Math.max(max, value);
        }
        return max;
    }

    static public NDArray<Integer> arange(int start, int stop, int step) {

        int length;
        if ((step == 0) || (step < 0 && stop >= start) || (step > 0 && start >= stop)) {
            length = 0;
        } else if (step < 0) {
            length = (stop - start + 1) / (step) + 1;
        } else {
            length = (stop - start - 1) / (step) + 1;
        }

        Integer[] data = new Integer[length];

        for (int i = start, j = 0; i < stop; i += step, j++) {
            data[j] = i;
        }

        return array(data);
    }

    @SafeVarargs
    static public <T> NDArray<T> concat(NDArray<T>... ndarrays){
        int length = 0;
        for (NDArray<T> ndarray: ndarrays) {
            if(ndarray.shape.length != ndarrays[0].shape.length) return null;
            for (int i = 1; i < ndarrays[0].shape.length; i++) {
                if(ndarray.shape[i] != ndarrays[0].shape[i]) return null;
            }
            length += ndarray.shape[0];
        }
        int[] shape = new int[ndarrays[0].shape.length];
        shape[0] = length;
        for (int i = 1; i < ndarrays[0].shape.length; i++) {
            shape[i] = ndarrays[0].shape[i];
        }
        NDArray<T> out = new NDArray<>(shape);
        int start = 0;
        for (NDArray<T> ndarray: ndarrays) {
            int stop = start + ndarray.shape[0];
            out.get(slice(start, stop)).set(ndarray);
            start = stop;
        }
        if(start != out.shape[0]) return null;
        return out;
    }

    static void print(Object... args) {
        StringBuilder string = new StringBuilder();
        for (Object arg : args) {
            if (string.length() != 0) string.append(" ");
            string.append(arg.toString());
        }
        System.out.println(string.toString());
    }

    private static int[] toArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    public NDArray<T> view() {
        return new NDArray<>(data, offsets);
    }

    public NDArray<T> copy() {
        NDArray<T> ndarray = new NDArray<>(shape);
        for (Iterator<int[]> it = indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            ndarray.setValue(indices, getValue(indices));
        }
        return ndarray;
    }

    public NDArray<T> reshape(int[] shape) {
        NDArray<T> ndarray = copy();
        int unknown = -1;
        int size = length(0);
        int rest = 1;
        int[] _shape = new int[shape.length];
        for (int i = 0; i < shape.length; i++) {
            int dim = shape[i];
            _shape[i] = dim;
            if (dim == -1) {
                if (unknown != -1) return null;
                unknown = i;
            } else {
                rest *= dim;
            }
        }
        if (unknown != -1) {
            if (size % rest != 0) return null;
            _shape[unknown] = size / rest;
        }

        ndarray.data.shape = _shape;
        ndarray.shape = ndarray.shape();
        return ndarray;
    }

    public T data(int index) {
        return data.get(index);
    }

    public void data(int index, T value) {
        data.set(index, value);
    }

    public int length(int dim) {
        if (dim >= shape.length) return 1;
        return shape[dim] * length(dim + 1);
    }

    public NDArray<T> get(Selection... selections) {
        if (selections.length > shape.length) return null;
        if (offsets == null) offsets = offsets();
        List<Offset> offsetList = new ArrayList<>();

        for (int i = 0, j = 0; i < offsets.length; i++) {
            if (offsets[i] instanceof Slicing) {
                Slicing slicing = (Slicing) offsets[i];
                if (j < selections.length) {
                    if (selections[j] instanceof Slice) {
                        // Slicing, slice
                        Slice slice = (Slice) selections[j];
                        offsetList.add(slicing.get(slice));
                    } else {
                        // Slicing, index
                        Index index = (Index) selections[j];
                        offsetList.add(slicing.get(index));
                    }
                    j++;
                } else {
                    // Slicing, copy
                    offsetList.add(slicing);
                }
            } else {
                // Indexing, copy
                Indexing indexing = (Indexing) offsets[i];
                offsetList.add(indexing);
            }
        }

        Offset[] offsets = offsetList.toArray(new Offset[0]);
        return new NDArray<>(data, offsets);
    }

    private Index index = new Index(0);
    public NDArray<T> get(int ind) {
        index.ind = ind;
        return get(index);
    }

    static Double Double(Float n){
        return (double)(float)n;
    }
    static Double Double(Integer n){
        return (double)(int)n;
    }
    static Float Float(Double n){
        return (float)(double)n;
    }
    static Float Float(Integer n){
        return (float)(int)n;
    }
    static Integer Integer(Double n){
        return (int)(double)n;
    }
    static Integer Integer(Float n){
        return (int)(float)n;
    }
    static Integer Integer(Boolean n){
        return n?1:0;
    }
    static Boolean Boolean(Integer n){
        return n != 0;
    }

    public void set(NDArray<T> src) {
        if(!Arrays.equals(shape, src.shape)){
            Broadcast<T> bc = new Broadcast<>(this, src);
            if (bc.shape == null || !Arrays.equals(bc.shape, shape)) return;
            for (int i = 0; bc.hasNext(); i++) {
                int[][] indices = bc.next();
                setValue(indices[0], src.getValue(indices[1]));
            }
            return;
        }
        for (Iterator<int[]> it = indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            setValue(indices, src.getValue(indices));
        }
    }

    private int[] shape() {
        if (offsets == null) return data.shape;
        List<Integer> shapeList = new ArrayList<>();
        for (Offset offset : offsets) {
            if (offset instanceof Slicing) {
                Slicing slicing = (Slicing) offset;
                shapeList.add(slicing.length);
            }
        }
        return toArray(shapeList);
    }

    private Offset[] offsets() {
        int N = data.shape.length;
        Offset[] offsets = new Offset[N];
        for (int i = 0; i < N; i++) {
            offsets[i] = slice().slicing(data.shape[i]);
        }
        return offsets;
    }

    private int getIndex(int[] indices) {
        if (indices.length != shape.length) return -1;

        int ind = 0;
        int length = 1;
        for (int i = data.shape.length - 1, j = indices.length - 1; i >= 0; i--) {
            int n;
            if (offsets != null) {
                if (offsets[i] instanceof Slicing) {
                    Slicing slicing = (Slicing) offsets[i];
                    n = slicing.get(indices[j]);
                    j--;
                } else {
                    Indexing indexing = (Indexing) offsets[i];
                    n = indexing.get();
                }
            } else {
                n = indices[i];
            }
            ind += n * length;
            length *= data.shape[i];
        }

        return ind;
    }

    public T getValue(int[] indices) {
        int ind = getIndex(indices);
        if (ind == -1) return null;

        return data(ind);
    }

    public T getValue(){
        if(shape.length > 0) return null;
        return data(0);
    }

    public T getValue(int i){
        return getValue(new int[]{i});
    }

    public void setValue(int[] indices, T value) {
        int ind = getIndex(indices);
        if (ind == -1) return;

        data(ind, value);
    }

    public <E> NDArray<E> astype(Function<T, E> valueOf){
        NDArray<E> ndarray = new NDArray<>(shape);
        for (Iterator<int[]> it = indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            ndarray.setValue(indices, valueOf.apply(getValue(indices)));
        }
        return ndarray;
    }

    public String toString() {
        if (shape.length == 0) return data(0).toString();

        StringBuilder string = new StringBuilder();
        if (shape.length == 1) {
            int[] indices = new int[1];
            for (int i = 0; i < shape[0]; i++) {
                if (string.length() > 0) string.append(", ");
                indices[0] = i;
                string.append(getValue(indices));
            }
        } else {
            for (int i = 0; i < shape[0]; i++) {
                if (string.length() > 0) string.append(", ");
                string.append(get(index(i)));
            }
        }
        return "[" + string + "]";
    }

    public Iterator<int[]> indexIterator() {
        return new IndexIterator(shape);
    }

    static class Operator {
        static Double add(Double a, Double b) {
            return a + b;
        }

        static Double sub(Double a, Double b) {
            return a - b;
        }

        static Double mul(Double a, Double b) {
            return a * b;
        }

        static Double div(Double a, Double b) {
            return a / b;
        }

        static Boolean gt(Double a, Double b){
            return a > b;
        }
        static Boolean gte(Double a, Double b){
            return a >= b;
        }
        static Boolean lt(Double a, Double b){
            return a < b;
        }
        static Boolean lte(Double a, Double b){
            return a <= b;
        }
        static Boolean eq(Double a, Double b){
            return a.equals(b);
        }

        static Boolean neq(Double a, Double b){
            return !a.equals(b);
        }
    }

    static public int argmax(NDArray<Double> ndarray){
        Double max = null;
        int index = -1;
        int i=0;
        for (Iterator<int[]> it = ndarray.indexIterator(); it.hasNext(); ) {
            int[] indices = it.next();
            Double value = ndarray.getValue(indices);
            if(max == null || value>max){
                max = value;
                index=i;
            }
            i++;
        }
        return index;
    }

    static class Broadcast<T> implements Iterator<int[][]> {
        public final int[] shape;

        NDArray<T>[] ndarrays;

        private IndexIterator indexIterator;
        private int[][] arrayIndices;

        @SafeVarargs
        public Broadcast(NDArray<T>... ndarrays) {
            this.ndarrays = ndarrays;
            this.shape = shape();
            if (shape != null) {
                indexIterator = new IndexIterator(shape);
                arrayIndices = new int[ndarrays.length][];
                for (int i = 0; i < ndarrays.length; i++) {
                    arrayIndices[i] = new int[ndarrays[i].shape.length];
                }
            }
        }

        private int[] shape() {
            int ndim = 0;
            for (NDArray<T> ndarray : ndarrays) ndim = Math.max(ndarray.shape.length, ndim);
            int[] shape = new int[ndim];

            for (int i = 0; i < ndim; i++) {
                int m = ndim - 1 - i;
                shape[m] = 1;
                for (NDArray<T> ndarray : ndarrays) {
                    int n = ndarray.shape.length - 1 - i;
                    if (n >= 0) {
                        if (shape[m] == 1) shape[m] = ndarray.shape[n];
                        else if (ndarray.shape[n] != 1 && ndarray.shape[n] != shape[m]) {
                            return null;
                        }
                    }
                }
            }

            return shape;
        }

        public boolean hasNext() {
            return indexIterator.hasNext();
        }

        public int[][] next() {
            int[] current = indexIterator.next();
            for (int i = 0; i < ndarrays.length; i++) {
                int[] indices = arrayIndices[i];
                int[] shape = ndarrays[i].shape;
                for (int j = 0; j < indices.length; j++) {
                    int n = indices.length - 1 - j;
//                    print(n + ", " + (current.length -1 -j));
                    indices[n] = shape[n] == 1 ? 0 : current[current.length - 1 - j];
                }
            }
            return arrayIndices;
        }
    }

    static abstract class Selection {
    }

    static private class Index extends Selection {
        int ind;

        Index(int ind) {
            this.ind = ind;
        }

        public Indexing indexing(int length) {
            return new Indexing(ind < 0 ? ind + length : ind);
        }

        public String toString() {
            return String.valueOf(ind);
        }
    }

    static private class Slice extends Selection {
        Integer start, stop, step;

        Slice(Integer start, Integer stop, Integer step) {
            this.start = start;
            this.stop = stop;
            this.step = step;
        }

        public Slicing slicing(int length) {
            Integer start = this.start,
                    stop = this.stop,
                    step = this.step;

            if (step == null) {
                step = 1;
            }

            int defStart = step < 0 ? length - 1 : 0;
            int defStop = step < 0 ? -1 : length;

            if (start == null) {
                start = defStart;
            } else {
                if (start < 0) start += length;
                if (start < 0) start = step < 0 ? -1 : 0;
                if (start >= length) start = step < 0 ? length - 1 : length;
            }

            if (stop == null) {
                stop = defStop;
            } else {
                if (stop < 0) stop += length;
                if (stop < 0) stop = step < 0 ? -1 : 0;
                if (stop >= length) stop = step < 0 ? length - 1 : length;
            }

            int sliceLength;
            if ((step == 0) || (step < 0 && stop >= start) || (step > 0 && start >= stop)) {
                sliceLength = 0;
            } else if (step < 0) {
                sliceLength = (stop - start + 1) / (step) + 1;
            } else {
                sliceLength = (stop - start - 1) / (step) + 1;
            }

            return new Slicing(start, stop, step, sliceLength);
        }

        public String toString() {
            return start + ":" + stop + ":" + step;
        }
    }

    static abstract private class Offset {
    }

    static private class Indexing extends Offset {
        private final int ind;

        public Indexing(int ind) {
            this.ind = ind;
        }

        public int get() {
            return ind;
        }

        public String toString() {
            return String.valueOf(ind);
        }
    }

    static private class Slicing extends Offset {
        final int start, stop, step;
        final int length;

        Slicing(int start, int stop, int step, int length) {
            this.start = start;
            this.stop = stop;
            this.step = step;
            this.length = length;
//            print(start+", "+stop+", "+step+", "+length);
        }

        public Slicing get(Slice slice) {
            Slicing sliced = slice.slicing(length);
            int start = this.start + sliced.start;
            int stop = this.start + sliced.stop;
            int step = this.step * sliced.step;
            return new Slicing(start, stop, step, sliced.length);
        }

        public Indexing get(Index index) {
            return new Indexing(get(index.indexing(length).get()));
        }

        public int get(int ind) {
            if (ind < 0) {
                ind += length;
            }
            return start + step * ind;
        }

        public String toString() {
            return start + ":" + stop + ":" + step;
        }
    }

    static class TypedArray<T> {
        final Object[] data;
        public int[] shape;

        public TypedArray(int[] shape) {
            int length = 1;
            for (int n : shape) length *= n;

            this.shape = shape;
            this.data = new Object[length];
        }

        @SuppressWarnings("unchecked")
        public T get(int index) {
            return (T) data[index];
        }

        public void set(int index, T value) {
            data[index] = value;
        }
    }

    static class IndexIterator implements Iterator<int[]> {
        private final int[] shape;
        private final int[] indices;
        private final int[] out;
        private boolean hasNext;

        public IndexIterator(int[] shape) {
            this.shape = shape;
            this.indices = new int[shape.length];
            this.hasNext = true;

            this.out = new int[shape.length];
        }

        public boolean hasNext() {
            return hasNext;
        }

        public int[] next() {
            int carry = 1;
            for (int i = indices.length - 1; i >= 0; i--) {
                out[i] = indices[i];

                indices[i] += carry;
                carry = 0;
                if (indices[i] >= shape[i]) {
                    indices[i] -= shape[i];
                    carry = 1;
                }
            }
            if (carry != 0) hasNext = false;
            return out;
        }
    }
}