
class Student {

    private String name;
    private int age;
    private String address;
    private float wallet;

    private Student(StudentBuilder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.address = builder.address;
        this.wallet = builder.wallet;
    }

    public static class StudentBuilder {

        private String name;
        private int age;
        private String address;
        private float wallet;

        // Mandatory parameter
        public StudentBuilder(String name) {
            this.name = name;
        }

        // Optional parameters
        public StudentBuilder setAge(int age) {
            this.age = age;
            return this;
        }

        // Optional parameters
        public StudentBuilder setAddress(String address) {
            this.address = address;
            return this;
        }

        // Optional parameters
        public StudentBuilder setWallet(float wallet) {
            this.wallet = wallet;
            return this;
        }

        public Student build() {
            return new Student(this);
        }
    }

    public String toString() {
        return "Student [name=" + name + ", age=" + age + ", address=" + address + ", wallet=" + wallet + "]";
    }
}

public class BuilderDesignPattern {
    public static void main(String[] args) {

        Student student = new Student.StudentBuilder("Pradeep")
                .setAge(28)
                .setAddress("SDA")
                .setWallet(1232.5f)
                .build();

        System.out.println(student);
    }
}