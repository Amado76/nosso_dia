CREATE TABLE beehome.books (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES beehome.families(id),
    title VARCHAR(300) NOT NULL CHECK (length(btrim(title)) > 0),
    author VARCHAR(200), isbn VARCHAR(32),
    total_pages INTEGER CHECK (total_pages > 0),
    cover_media_id UUID,
    created_by UUID NOT NULL REFERENCES beehome.users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, family_id),
    FOREIGN KEY (cover_media_id, family_id) REFERENCES beehome.media(id, family_id)
);
CREATE INDEX books_family_created ON beehome.books(family_id, created_at DESC, id DESC);
CREATE INDEX books_cover ON beehome.books(cover_media_id) WHERE cover_media_id IS NOT NULL;
CREATE TABLE beehome.child_books (
    id UUID PRIMARY KEY, family_id UUID NOT NULL, child_id UUID NOT NULL, book_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNED','READING','COMPLETED','ABANDONED')),
    started_on DATE, completed_on DATE,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (family_id, child_id) REFERENCES beehome.family_members(family_id, id),
    FOREIGN KEY (book_id, family_id) REFERENCES beehome.books(id, family_id),
    UNIQUE (id, family_id, child_id),
    CHECK ((status = 'COMPLETED') = (completed_on IS NOT NULL)),
    CHECK (completed_on >= started_on)
);
CREATE UNIQUE INDEX child_books_one_active ON beehome.child_books(child_id, book_id) WHERE status IN ('PLANNED','READING');
CREATE INDEX child_books_history ON beehome.child_books(family_id, child_id, created_at DESC, id DESC);
CREATE INDEX child_books_book ON beehome.child_books(book_id);
CREATE TABLE beehome.reading_sessions (
    id UUID PRIMARY KEY, family_id UUID NOT NULL, child_id UUID NOT NULL, child_book_id UUID NOT NULL,
    date DATE NOT NULL,
    minutes INTEGER CHECK (minutes >= 0), pages_read INTEGER CHECK (pages_read >= 0),
    start_page INTEGER CHECK (start_page >= 0), end_page INTEGER CHECK (end_page >= start_page),
    notes VARCHAR(2000),
    created_by UUID NOT NULL REFERENCES beehome.users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (child_book_id, family_id, child_id) REFERENCES beehome.child_books(id, family_id, child_id),
    CHECK ((start_page IS NULL) = (end_page IS NULL)),
    CHECK (start_page IS NULL OR (pages_read IS NOT NULL AND pages_read = end_page - start_page))
);
CREATE INDEX reading_sessions_history ON beehome.reading_sessions(family_id, child_id, date DESC, created_at DESC, id DESC);
CREATE INDEX reading_sessions_journey ON beehome.reading_sessions(child_book_id, date DESC, created_at DESC, id DESC);
