export interface Branch {
  id?: string;
  _id?: string;
  name: string;
  slug: string;
  zone?: string;
  tagline?: string;
  address?: string;
  description?: string;
  mainImage?: string;
  vibe?: string[];
  location?: any;
}

export interface Suite {
  id?: string;
  _id?: string;
  name: string;
  slug?: string;
  type?: string;
  description?: string;
  basePrice?: number;
  pricePerNight?: number;
  seasonalPrice?: number;
  totalPrice?: number;
  maxGuests?: number;
  size?: number;
  amenities?: string[];
  features?: string[];
  branchId?: string;
  branch?: Branch;
  mainImage?: string;
  images?: string[];
  available?: boolean;
  isAvailable?: boolean;
}

export interface Experience {
  id?: string;
  _id?: string;
  name: string;
  title?: string;
  description?: string;
  shortDescription?: string;
  price: number;
  mainImage?: string;
  image?: string;
  imageUrl?: string;
  icon?: string;
  durationHours?: number;
  category?: string;
}

export interface User {
  id?: string;
  _id?: string;
  email: string;
  name: string;
  phone?: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  user: User;
  data?: {
    token: string;
    user: User;
  };
}

export interface Booking {
  id?: string;
  _id?: string;
  user?: User | string;
  userId?: string;
  suite?: Suite;
  suiteId?: string;
  branch?: Branch;
  checkIn: string;
  checkOut: string;
  nights?: number;
  guests?: number;
  children?: number;
  totalPrice: number;
  paidAmount?: number;
  refundedAmount?: number;
  balanceDue?: number;
  status: string;
  paymentStatus?: string;
  bookingCode?: string;
  isModifiable?: boolean;
  isCancelable?: boolean;
  experiences?: Experience[];
  guestName?: string;
  guestEmail?: string;
  guestPhone?: string;
}
