export interface Branch {
  id: string;
  name: string;
  slug: string;
  location: string;
}

export interface Suite {
  id: string;
  name: string;
  slug: string;
  description: string;
  pricePerNight: number;
  capacity: number;
  amenities: string[];
  branchId: string;
  branch?: Branch;
  images: string[];
}

export interface Experience {
  id: string;
  title: string;
  description: string;
  price: number;
  imageUrl: string;
}

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Booking {
  id: string;
  userId: string;
  suiteId: string;
  checkIn: string;
  checkOut: string;
  totalPrice: number;
  status: string;
  suite?: Suite;
}
