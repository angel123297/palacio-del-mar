import Navbar from '../components/Navbar.jsx';
import PromoBanner from '../components/PromoBanner.jsx';
import Hero from '../components/Hero.jsx';
import BookingBar from '../components/BookingBar.jsx';
import About from '../components/About.jsx';
import RoomsSection from '../components/RoomsSection.jsx';
import ExperiencesSection from '../components/ExperiencesSection.jsx';
import DiningSection from '../components/DiningSection.jsx';
import SpaSection from '../components/SpaSection.jsx';
import LocationSection from '../components/LocationSection.jsx';
import Footer from '../components/Footer.jsx';
import AIChatWidget from '../components/AIChatWidget.jsx';
import BookingModal from '../components/BookingModal.jsx';

export default function HomePage() {
  return (
    <>
      <PromoBanner />
      <Navbar />
      <Hero />
      <BookingBar />
      <About />
      <RoomsSection />
      <ExperiencesSection />
      <DiningSection />
      <SpaSection />
      <LocationSection />
      <Footer />
      <AIChatWidget />
      <BookingModal />
    </>
  );
}
