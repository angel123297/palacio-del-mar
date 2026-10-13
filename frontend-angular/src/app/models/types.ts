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
  highlights?: any[];
}

export interface Suite {
  id?: string;
  _id?: string;
  name: string;
  slug?: string;
  type?: string;
  description?: string;
  basePrice?: number;
  originalPrice?: number;
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
  featured?: boolean;
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
  emailVerified?: boolean;
  name: string;
  phone?: string;
  role: string;
  profile?: {
    phone?: string;
    address?: any;
    documentId?: string;
    documentType?: string;
    avatar?: string;
  };
}

export interface ApiResponse<T> {
  data?: T;
  message?: string;
  success?: boolean;
}

export interface AuthResponse {
  token?: string;
  user: User;
  data?: {
    token?: string;
    user: User;
  };
}

export interface LoginDTO {
  email: string;
  password?: string;
}

export interface RegisterDTO {
  name: string;
  email: string;
  password?: string;
  phone?: string;
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
  experiences?: any[];
  guestName?: string;
  guestEmail?: string;
  guestPhone?: string;
  airportPickup?: boolean;
  petFriendly?: boolean;
  needsCrib?: boolean;
  guaranteeDeposit?: number;
  guaranteeRefunded?: boolean;
  foodOrders?: Array<{
    dishName?: string;
    type?: string;
    includesBreakfastCombo?: boolean;
    price?: number;
  }>;
  appliedPromotionId?: string;
  appliedPromotionName?: string;
  appliedPromotionDiscountPercent?: number;
  checkedIn?: boolean;
  checkedOut?: boolean;
  smartLockPin?: string;
  expiresAt?: string;
}

export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

export interface DashboardMetricsDTO {
  activeBookings: number;
  totalRevenue: number;
  averageOccupancy: number; // e.g. 84 for 84%
  pendingCheckins?: number; // mostly for host
  newClubMembers?: number; // mostly for admin
  totalBookings: number;
  totalUsers?: number;
  totalSuites?: number;
}

