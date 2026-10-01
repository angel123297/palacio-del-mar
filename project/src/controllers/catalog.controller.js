import Suite from '../models/Suite.js'; 

import Experience from '../models/Experience.js';



// 1. Obtener tipos de suites (Para el filtro de búsqueda)

export const getSuiteTypes = async (req, res) => {

    try {

        const types = await Suite.distinct('type');

        res.status(200).json(types);

    } catch (error) {

        res.status(500).json({ message: 'Error al obtener tipos de suite', error: error.message });

    }

};



// 2. Obtener categorías de experiencias (Para el menú del frontend)

export const getExperienceCategories = async (req, res) => {

    try {

        const categories = await Experience.distinct('category');

        res.status(200).json(categories);

    } catch (error) {

        res.status(500).json({ message: 'Error al obtener categorías', error: error.message });

    }

};



// 3. Obtener experiencias destacadas (Límite de 5, ideal para el home)

export const getFeaturedExperiences = async (req, res) => {

    try {

        const featured = await Experience.find({ isFeatured: true }).limit(5);

        res.status(200).json(featured);

    } catch (error) {

        res.status(500).json({ message: 'Error al obtener destacadas', error: error.message });

    }

};
