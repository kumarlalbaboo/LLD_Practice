
interface Prototype {
    Prototype clone();
}

class Address {
    private String city;

    public Address(String city) {
        this.city = city;
    }

    public Address(Address address) {
        this.city = address.city;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}

class Employee implements Prototype {
    private String name;
    private int age;
    private Address address;

    public Employee(String name, int age, String city) {
        this.name = name;
        this.age = age;
        this.address = new Address(city);
    }

    public Employee(Employee employee) {
        this.name = employee.name;
        this.age = employee.age;
        this.address = new Address(employee.address);
    }

    @Override
    public Prototype clone() {
        return new Employee(this);
    }

    void print() {
        System.out.println("Name='" + name + "', Age=" + age + ", Address=" + address .getCity());
    }

    public void updateCity(String city) {
        this.address.setCity(city);
    }

}

public class PrototypeDesignPattern {
    public static void main(String[] args) {

        System.out.println("Prototype Design Pattern Example - Deep Copy");

        Employee original = new Employee("John Doe", 30, "New York");

        // Clone the original employee
        Employee clonedEmployee = (Employee) original.clone();

        System.out.println("Before modifying the cloned employee's address:");
        original.print();
        clonedEmployee.print();

        // Modify the cloned employee's address
        clonedEmployee.updateCity("Los Angeles");

        // Print both employees to see the effect of cloning
        System.out.println("Original Employee:");
        original.print();
        
        System.out.println("Modified Cloned Employee:");
        clonedEmployee.print();
    }
}
