package test.com.xunlei.netty;

import com.xunlei.json.JSONUtil;

/**
 * @author ZengDong
 * @since 2010-6-2 上午12:42:32
 */
public class TestJson {

    private class TestBean {

        public long a = 1;
        public boolean b = true;
        public String c = "yeah";
        public char d = '\t';
        public char e = '/';

        public long getA() {
            return a;
        }

        public void setA(long a) {
            this.a = a;
        }

        public boolean isB() {
            return b;
        }

        public void setB(boolean b) {
            this.b = b;
        }

        public String getC() {
            return c;
        }

        public void setC(String c) {
            this.c = c;
        }

        public char getD() {
            return d;
        }

        public void setD(char d) {
            this.d = d;
        }

        public char getE() {
            return e;
        }

        public void setE(char e) {
            this.e = e;
        }

    }

    public static void main(String[] args) {
        System.out.println(JSONUtil.fromObject(new TestJson().new TestBean()));
    }
}
